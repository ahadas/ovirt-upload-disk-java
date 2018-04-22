package demo.demo;

import static org.ovirt.engine.sdk4.ConnectionBuilder.connection;

import java.io.File;
import java.io.FileInputStream;
import java.io.InputStream;
import java.io.OutputStream;
import java.math.BigInteger;
import java.net.URL;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.security.KeyStore;
import java.security.cert.CertificateFactory;
import java.security.cert.X509Certificate;
import java.util.Collections;
import java.util.function.Consumer;

import javax.net.ssl.HttpsURLConnection;
import javax.net.ssl.SSLContext;
import javax.net.ssl.TrustManagerFactory;

import org.ovirt.engine.sdk4.Connection;
import org.ovirt.engine.sdk4.internal.containers.DiskContainer;
import org.ovirt.engine.sdk4.internal.containers.ImageContainer;
import org.ovirt.engine.sdk4.internal.containers.ImageTransferContainer;
import org.ovirt.engine.sdk4.services.DataCentersService;
import org.ovirt.engine.sdk4.services.DataCentersService.ListRequest;
import org.ovirt.engine.sdk4.services.DisksService;
import org.ovirt.engine.sdk4.services.ImageTransferService;
import org.ovirt.engine.sdk4.services.ImageTransfersService;
import org.ovirt.engine.sdk4.services.StorageDomainsService;
import org.ovirt.engine.sdk4.services.SystemService;
import org.ovirt.engine.sdk4.types.DataCenter;
import org.ovirt.engine.sdk4.types.Disk;
import org.ovirt.engine.sdk4.types.DiskFormat;
import org.ovirt.engine.sdk4.types.DiskStatus;
import org.ovirt.engine.sdk4.types.ImageTransfer;
import org.ovirt.engine.sdk4.types.ImageTransferPhase;
import org.ovirt.engine.sdk4.types.StorageDomain;

public class App 
{
    public static void main( String[] args ) throws Exception
    {
        System.out.println( "Hello World!" );
        Connection connection = connection()
				  .url("http://dubi.tlv.redhat.com:8080/ovirt-engine/api")
				  .user("admin@internal")
				  .password("1")
//				  .trustStoreFile("truststore.jks")
				  .build();

		// Get the reference to the system service:
		SystemService systemService = connection.systemService();
		DataCentersService dcsService = systemService.dataCentersService();
		ListRequest lr = dcsService.list();
		lr.send().dataCenters().forEach(new Consumer<DataCenter>() {
			public void accept(DataCenter dc) {
				System.out.println("dc: " + dc.name());
			}
		});

		DisksService disksService = systemService.disksService();
		disksService.list().send().disks().forEach(new Consumer<Disk>() {
			public void accept(Disk disk) {
				System.out.println("Disk " + disk.name() + ":");
				System.out.println("    size        = " + disk.provisionedSize());
				System.out.println("    actual-size = " + disk.actualSize());
//				System.out.println("    bootable    = " + disk.bootable());
			}
		});

		StorageDomainsService sdsService = systemService.storageDomainsService();
		StorageDomain defaultSd = null;
		for (StorageDomain sd : sdsService.list().send().storageDomains()) {
			if (sd.name().equals("Default")) {
				defaultSd = sd;
			}
		}

		File file = new File("/tmp/0150a8e0-1f64-42a2-a937-d6e18f9f1cbc");
//		File file = new File("/tmp/arik.txt");
//		byte[] content = Files.readAllBytes(Paths.get(file.getAbsolutePath()));
//		System.out.println("uploading file with: " + new String(content));

		System.out.println("Creating disk...");
		DiskContainer disk = new DiskContainer();
		disk.name("mu");
		disk.description("just a test");
		disk.format(DiskFormat.RAW);
		disk.provisionedSize(BigInteger.valueOf(8).multiply(BigInteger.valueOf(2).pow(30)));
		disk.format(DiskFormat.RAW);
		disk.sparse(true);
		disk.storageDomains(Collections.singletonList(defaultSd));
		Disk disk2 = disksService.add().disk(disk).send().disk();

		do {
			Thread.sleep(3000);
		} while (disksService.diskService(disk2.id()).get().send().disk().status() != DiskStatus.OK);
		System.out.println("Disk has been created!");

		System.out.println("Creating transfer session...");
		ImageTransfersService transfersService = systemService.imageTransfersService();
		ImageTransferContainer transfer2 = new ImageTransferContainer();
		ImageContainer image = new ImageContainer();
		image.id(disk2.id());
		transfer2.image(image);
		ImageTransfer transfer = transfersService.add().imageTransfer(transfer2).send().imageTransfer();

		do {
			Thread.sleep(1000);
		} while (transfer.phase() == ImageTransferPhase.INITIALIZING);
		System.out.println("Transfer session has been created!");

		setTrustStore();
		System.setProperty("sun.net.http.allowRestrictedHeaders", "true");

		URL url = new URL(transfer.proxyUrl());
//		SSLContext context = new SSLContext
		HttpsURLConnection https = (HttpsURLConnection) url.openConnection();
		https.setRequestProperty("PUT", url.getPath());
		long length = file.length();
		System.out.println("length = " + length);
//		https.setFixedLengthStreamingMode(length);
//		System.out.println("content length = " + https.getContentLengthLong());
		https.setRequestProperty("Content-Length", String.valueOf(length));
		https.setRequestMethod("PUT");
//		https.setDefaultUseCaches(false);
		https.setChunkedStreamingMode(64*1024);
//		https.setChunkedStreamingMode(-1);
//		https.setUseCaches(false);
		https.setDoOutput(true);
		https.connect();

		OutputStream os = https.getOutputStream();
		InputStream is = new FileInputStream(file);
		byte[] buf = new byte[128 * 1024];
		int read = 0;
		System.out.println("transforming...");

		/*int len = is.read(buf);
		while (len != -1) {
		    os.write(buf, 0, len);
		    len = is.read(buf);
		    read += len;
		    System.out.println("transformed " + len + ", " + read);
		}*/
		do {
			int readNow = is.read(buf);
			os.write(buf, 0, readNow);
			os.flush();
			read += readNow;
			System.out.println("transformed " + readNow + ", " + read);
		} while(read < length);

		int responseCode = https.getResponseCode();
		
		System.out.println("response code  = " + responseCode);

		is.close();
		os.close();

		System.out.println("Terminating transfer session...");
		ImageTransferService transferService = systemService.imageTransfersService().imageTransferService(transfer.id());
		transferService.finalize_().send();
		https.disconnect();
		
		// Always remember to close the connection when finished:
		connection.close();
    }

    
    public static void setTrustStore() throws Exception {
        TrustManagerFactory trustManagerFactory = TrustManagerFactory.getInstance("X509");
        KeyStore keystore = KeyStore.getInstance(KeyStore.getDefaultType());
        InputStream is = new FileInputStream("/tmp/ca.pem");
        CertificateFactory cf = CertificateFactory.getInstance("X.509");
        X509Certificate caCert = (X509Certificate)cf.generateCertificate(is);

        TrustManagerFactory tmf = TrustManagerFactory
            .getInstance(TrustManagerFactory.getDefaultAlgorithm());
        KeyStore ks = KeyStore.getInstance(KeyStore.getDefaultType());
        ks.load(null); // You don't need the KeyStore instance to come from a file.
        ks.setCertificateEntry("caCert", caCert);

        tmf.init(ks);

        SSLContext sslContext = SSLContext.getInstance("TLS");
        sslContext.init(null, tmf.getTrustManagers(), null);
        SSLContext.setDefault(sslContext);
        /*
        trustManagerFactory.init(keystore);
        TrustManager[] trustManagers = trustManagerFactory.getTrustManagers();
        SSLContext sc = SSLContext.getInstance("SSL");
        sc.init(null, trustManagers, null);
        SSLContext.setDefault(sc);*/
    }
}

package test;

import static org.ovirt.engine.sdk4.ConnectionBuilder.connection;

import org.ovirt.engine.sdk4.Connection;
import org.ovirt.engine.sdk4.services.SystemService;

public class Main {

	public static void main(String[] args) throws Exception {
		Connection connection = connection()
				  .url("http://dubi.tlv.redhat.com:8080/ovirt-engine/api")
				  .user("admin@internal")
				  .password("1")
//				  .trustStoreFile("truststore.jks")
				  .build();

		// Get the reference to the system service:
		SystemService systemService = connection.systemService();

		// Always remember to close the connection when finished:
		connection.close();
	}

}

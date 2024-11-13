<p align="center">
<img src="./cdpg.png" width="300">
</p>

# Modules

This document contains the information of the configurations to setup various services and dependencies in order to
bring up the DX rs-proxy Server.
Please find the example configuration
file [here](https://github.com/datakaveri/iudx-rs-proxy/blob/main/examples/configs/config-dev.json). While running the
server, config.json file could
be added [secrets](https://github.com/datakaveri/iudx-rs-proxy/tree/main/secrets/all-verticles-configs).
```console
  cp examples/configs/config-dev.json secrets/all-verticles-configs/
```

```
Configs directory:

secrets/all-verticles-configs/
├── config-dev.json
├── keystore.jks
└── keystore.p12
```

## Other Configuration

| Key Name   | Value Datatype | Value Example          | Description                                                 |
|:-----------|:--------------:|:-----------------------|:------------------------------------------------------------|
| version    |     Float      | 1.0                    | config version                                              |
| zookeepers |     Array      | zookeeper              | zookeeper configuration to deploy clustered vert.x instance |
| clusterId  |     String     | adex-rs-proxy-clusterr | cluster id to deploy clustered vert.x instance              |

## CommonConfig

| Key Name            | Datatype | Example      | Description                                                                                                                  |
|:--------------------|:--------:|:-------------|:-----------------------------------------------------------------------------------------------------------------------------|
| dxApiBasePath       |  String  | /auth/v1     | API base path for DX AAA server. Reference : [link](https://swagger.io/docs/specification/2-0/api-host-and-base-path/)       |
| dxAuthBasePath      |  String  | /ngsi-ld/v1  | API base path for DX rs-proxy-sever. Reference : [link](https://swagger.io/docs/specification/2-0/api-host-and-base-path/)   |
| dxCatalogueBasePath |  String  | /iudx/cat/v1 | API base path for DX Catalogue server. Reference : [link](https://swagger.io/docs/specification/2-0/api-host-and-base-path/) |
| isAdexInstance      | boolean  | true/false   | A flag indicating whether the deployment instance is ADex (true/false).                                                      |
| isTimeLimitEnabled  | boolean  | true/false   | A flag that controls the time span of the request (true/false).                                                              |

## ApiServerVerticle

| Key Name          | Value Datatype | Value Example | Description                                                    |
|:------------------|:--------------:|:--------------|:---------------------------------------------------------------|
| verticleInstances |    integer     | 1             | Number of instances required for vertical scaling of services. |
| port              |    integer     | 8080          | Port for running the instance DX Rs-proxy Server               |
| ssl               |    boolean     | true/false    | Determines whether SSL is enabled for secure communication.    |
| production        |    boolean     | true/false    | Specifies if the instance is in production mode (true/false).  |
| keystore          |     String     | path/to/file  | File path to the keystore containing SSL certificates.         |
| keystorePassword  |     String     | xyz           | Password for accessing the keystore.                           |

## AuthenticationVerticle

| Key Name          | Value Datatype | Value Example    | Description                                                           |
|:------------------|:--------------:|:-----------------|:----------------------------------------------------------------------|
| verticleInstances |    integer     | 1                | Number of instances required for vertical scaling of services.        |
| audience          |     String     | rs.abc.io        | The intended recipient (audience) of the JWT.                         |
| authServerHost    |     String     | auth.example.com | Hostname or IP address of the authentication server.                  |
| jwtIgnoreExpiry   |    boolean     | true/false       | Flag to indicate whether JWT expiry should be ignored.                |
| jwtLeeWay         |    integer     | 5                | Number of seconds to allow as leeway for token expiration validation. |
| catServerHost     |     String     | cat.example.com  | Hostname or IP address of the catalogue server.                       |
| catServerPort     |    integer     | 443              | Port on which the catalogue server runs, e.g., 443 for HTTPS.         |

## CacheVerticle

| Key Name          | Value Datatype | Value Example   | Description                                                    |
|:------------------|:--------------:|:----------------|:---------------------------------------------------------------|
| verticleInstances |    integer     | 1               | Number of instances required for vertical scaling of services. |
| catServerHost     |     String     | cat.example.com | Hostname or IP address of the catalogue server.                |
| catServerPort     |     String     | 443             | Port on which the catalogue server runs, e.g., 443 for HTTPS.  |

## DatabaseVerticle

| Key Name          | Value Datatype | Value Example       | Description                                                        |
|:------------------|:--------------:|:--------------------|:-------------------------------------------------------------------|
| verticleInstances |    integer     | 1                   | Number of instances required for vertical scaling of services.     |
| databaseIp        |     String     | database.example.in | IP address of the database server.                                 |
| databasePort      |    integer     | 5432                | Port on which the database is running (e.g., 5432 for PostgreSQL). |
| databaseName      |     String     | my_database         | Name of the database                                               |
| databaseUserName  |     String     | dbUserName          | Username for database authentication.                              |
| databasePassword  |     String     | dbPassword          | Password for database authentication.                              |
| poolSize          |    integer     | 10                  | Number of database connections allowed in the pool.                |

## MeteringVerticle

| Key Name                  | Value Datatype | Value Example       | Description                                                                        |
|:--------------------------|:--------------:|:--------------------|:-----------------------------------------------------------------------------------|
| verticleInstances         |    integer     | 1                   | Number of instances required for vertical scaling of services.                     |
| meteringDatabaseIP        |     String     | database.example.in | IP address of the metering database.                                               |
| meteringDatabasePort      |    integer     | 5432                | Port number on which the metering database is running (e.g., 5432 for PostgreSQL). |
| meteringDatabaseName      |     String     | meteringDB          | Name of the metering database.                                                     |
| meteringDatabaseUserName  |     String     | dbUserName          | Username for the metering database authentication.                                 |
| meteringDatabasePassword  |     String     | dbPassword          | Password for the metering database authentication.                                 |
| meteringDatabaseTableName |     String     | metering_table      | Name of the table in the metering database used for fetch  metering data.          |
| meteringPoolSize          |    integer     | 10                  | Number of database connections allowed in the pool.                                |

## DatabrokerVerticle

| Key Name                    | Value Datatype | Value Example         | Description                                                                    |
|:----------------------------|:--------------:|:----------------------|:-------------------------------------------------------------------------------|
| verticleInstances           |    integer     | 1                     | Number of instances required for vertical scaling of services.                 |
| dataBrokerIP                |     String     | example.databroker.in | IP address of the data broker server.                                          |
| dataBrokerPort              |    integer     | 5672                  | Port number on which the data broker service is running (e.g., 5672 for AMQP). |
| prodVhost                   |     String     | IUDX                  | Virtual host for the production environment.                                   |
| internalVhost               |     String     | IUDX-INTERNAL         | Virtual host for internal communications.                                      |
| externalVhost               |     String     | IUDX-EXTERNAL         | Virtual host for external communications.                                      |
| dataBrokerUserName          |     String     | brokerUser            | Username for the data broker authentication.                                   |
| dataBrokerPassword          |     String     | brokerPassword        | Password for the data broker authentication.                                   |
| dataBrokerManagementPort    |    integer     | 15672                 | Port for accessing the data broker's management interface (e.g., RabbitMQ).    |
| connectionTimeout           |    integer     | 6000                  | Timeout in milliseconds for establishing a connection to the data broker.      |
| requestedHeartbeat          |    integer     | 60                    | Heartbeat interval in seconds for AMQP connections.                            |
| handshakeTimeout            |    integer     | 6000                  | Timeout in milliseconds for completing the AMQP handshake.                     |
| requestedChannelMax         |    integer     | 5                     | Maximum number of channels that can be open on a single connection.            |
| networkRecoveryInterval     |    integer     | 500                   | Time in milliseconds before retrying to reconnect after a network failure.     |
| automaticRecoveryEnabled    |    boolean     | true                  | Whether automatic recovery of connections is enabled (true/false).             |
| adapterQueryPublishExchange |     String     | rpc-adapter-requests  | Name of the exchange to publish adapter queries.                               |
| adapterQueryReplyQueue      |     String     | rpc-adapter-queue     | Name of the queue to receive replies from adapter queries.                     |
| brokerAmqpsPort             |    integer     | 5671                  | Port number for secure AMQP (AMQPS) connections.                               |

## ConsentLoggingVerticle

| Key Name          | Value Datatype | Value Example     | Description                                                    |
|:------------------|:--------------:|:------------------|:---------------------------------------------------------------|
| verticleInstances |    integer     |                   | Number of instances required for vertical scaling of services. |
| certFileName      |     String     | /path/to/cert.pem | File path to the certificate file used for secure signed.      |
| password          |     String     | yourPassword123   | Password for accessing secured cert file                       |

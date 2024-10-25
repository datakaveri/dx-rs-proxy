<p align="center">
<img src="./cdpg.png" width="300">
</p>

# Frequently Asked Questions (FAQs)

1. How is the Resource Proxy Server different from the Resource Server?

- While there are similarities between the two, the key difference is that the Resource Server serves data from its own
  environment and database, whereas the Resource Proxy Server doesn't have a dedicated database. Instead, it streams
  search requests to RabbitMQ (RMQ), where an adaptor processes the query and returns the response to the proxy server
  via RMQ. Additionally, the proxy server allows data providers to store and manage their data in their preferred
  location or environment.

2. How do I request for a new feature to be added or change in an existing feature?

- Please create an issue [here](https://github.com/datakaveri/iudx-rs-proxy/issues)

3. What do we do when there is any error during flyway migration?

- We could run this command `mvn flyway:repair` and do the flyway migration again
  -If the error persists, it needs to be resolved manually and a backup of the database could be taken from postgres if
  the table needs to be changed

4. “Request could not be created, as resource was not found” - even if the resource is found while creating access
   request

- This error occurs when the resource proxy server URL that the consumer is associated to while requesting the API, does
  not
  match with the resource server URL of the resource item

5. What types of search functionalities does the DX Resource Server support?

- The server supports:
    - Spatial Search: Search using Circle, Polygon, Bounding Box (Bbox), and Linestring.
    - Temporal Search: Search based on time, including Before, During, and After criteria.
    - Attribute Search: Search based on specific resource attributes.
    - Latest Search: Give the packet of latest data available on the server.
    - Complex Search: Combination of Spatial Search, Temporal Search, Attribute Search
    - Async Search: Allows users to submit queries that are processed in the background, enabling them to retrieve
      results later without waiting for immediate completion. It is ideal for handling large datasets or time-consuming
      requests, with results stored and managed asynchronously.

6. Does the DX Resource proxy server support encrypted data access?

- Yes, the DX Resource proxy server supports encrypted data access and its handle bye the adaptor.

7. Is consent logging required for all requests?

- No, consent logging is only required for resources with a PII (Personally Identifiable Information) access policy. For
  other resources, consent logging is not necessary.

8. Can we bypass the consent logging mechanism?

- Yes, consent logging is configurable. If it's not required, it can be disabled through the configuration file.
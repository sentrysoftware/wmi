# WMI Java Client

The WMI Java Client is a library designed for interacting with the WMI services on Windows systems. The library facilitates the execution of WMI Query Language (WQL) queries making it easy to retrieve information about system.
See **[Project Documentation](https://sentrysoftware.github.io/wmi)** and the [Javadoc](https://sentrysoftware.github.io/wmi/apidocs) for more information on how to use this library in your code.

# How to run the WMI Client inside Java

Add WMI in the list of dependencies in your [Maven **pom.xml**](https://maven.apache.org/pom.html):

```xml
    <dependencies>
        <!-- [...] -->
        <dependency>
            <groupId>${project.groupId}</groupId>
            <artifactId>${project.artifactId}</artifactId>
            <version>${project.version}</version>
        </dependency>
    </dependencies>
```

Invoke the WMI Client:

```java
    public static void main(String[] args) throws Exception {

        final String hostname = "your-hostname";
        final String username = "your-username";
        final char[] password = "your-password".toCharArray();
        final String namespace = "your-namespace";
        final String wqlQuery = "your-wql-query";
        final int timeout = 5000;

        final String networkResource = WmiHelper.createNetworkResource(hostname, namespace);

        try (WmiWbemServices wbemServices = WmiWbemServices.getInstance(networkResource, username, password)) {

            // Submit the WQL query
            final List<Map<String, Object>> resultRows = wbemServices.executeWql(wqlQuery, timeout * 1000);

            // Extract the list of properties from the result, with same order as in the WQL query
            final List<String> propertyList = WmiHelper.extractPropertiesFromResult(resultRows, wqlQuery);
            propertyList.forEach(property -> {
                System.out.print(property);
                System.out.print(";");
            });
            System.out.println();
            // And print the result
            final WmiStringConverter stringConverter = new WmiStringConverter("|", false);
            resultRows.forEach(row -> {
                propertyList.forEach(property -> {
                    System.out.print(stringConverter.convert(row.get(property)));
                    System.out.print(";");
                });
                System.out.println();
            });
        }
    }

```
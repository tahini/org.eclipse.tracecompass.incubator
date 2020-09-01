# Eclipse Trace Compass Server

## Table of Contents

<!-- START doctoc generated TOC please keep comment here to allow auto update -->
<!-- DON'T EDIT THIS SECTION, INSTEAD RE-RUN doctoc TO UPDATE -->


- [Compiling manually](#compiling-manually)
- [Running the server](#running-the-server)
- [Example REST Commands](#example-rest-commands)
  - [Open a trace](#open-a-trace)
  - [Getting list of traces](#getting-list-of-traces)
  - [Getting events (events table)](#getting-events-events-table)
  - [Getting Filtered events](#getting-filtered-events)
  - [Start DiskIOAnalsis](#start-diskioanalsis)
  - [Get Disk IO Analysis](#get-disk-io-analysis)
  - [Get XY View data for Disk IO Analysis](#get-xy-view-data-for-disk-io-analysis)
- [Run the Server with SSL](#run-the-server-with-ssl)

<!-- END doctoc generated TOC please keep comment here to allow auto update -->

## Compiling manually

`mvn clean install`

## Running the server

```
$ cd trace-server/org.eclipse.tracecompass.incubator.trace.server.product/target/products/traceserver/linux/gtk/x86_64/trace-compass-server/
$ ./tracecompss-server`
```

OpenAPI REST specification:
The REST API is documented using the OpenAPI specification in the API.json file.
The file can be opened with an IDE plug-in, or Swagger tools.
For more information, see https://swagger.io/docs/.

## Example REST Commands

### Open a trace

```
$ curl -X POST \
  http://localhost:8080/tracecompass/traces \
  -H 'accept: application/json' \
  -H 'cache-control: no-cache' \
  -H 'content-type: application/x-www-form-urlencoded' \
  -d 'name=trace2&path=/home/user/git/tracecompass-test-traces/ctf/src/main//resources/trace2'
```

### Getting list of traces

```
$ curl -X GET \
  http://localhost:8080/tracecompass/traces \
  -H 'accept: application/json' \
  -H 'cache-control: no-cache'
```

### Getting events (events table)

```
$ curl -X GET \
  'http://localhost:8080/tracecompass/eventTable?name=trace2&low=10000&size=20' \
  -H 'accept: application/json' \
  -H 'cache-control: no-cache' \
  -H 'content-type: application/x-www-form-urlencoded'
```
  
### Getting Filtered events

```
$ curl -X PUT \
   'http://localhost:8080/tracecompass/eventTable?name=trace2&low=0&size=20' \
   -H 'accept: application/json' \
   -H 'cache-control: no-cache' \
   -H 'content-type: application/x-www-form-urlencoded' \
   -d 'Contents=ret.*'
```

### Start DiskIOAnalsis

```
$ curl -X POST \
  'http://localhost:8080/tracecompass/DiskActivityView?name=trace2' \
  -H 'accept: application/json' \
  -H 'cache-control: no-cache' \
  -H 'content-type: application/x-www-form-urlencoded'
```

### Get Disk IO Analysis

```
$ curl -X GET \
  http://localhost:8080/tracecompass/DiskActivityView \
  -H 'accept: application/json' \
  -H 'cache-control: no-cache'
```
 
### Get XY View data for Disk IO Analysis

```
$ curl -X GET \
  'http://localhost:8080/tracecompass/DiskActivityView/trace2?start=1331668247314038062&end=1331668247324038062&resolution=275520' \
  -H 'accept: application/json' \
  -H 'cache-control: no-cache' \
  -H 'content-type: application/x-www-form-urlencoded'
```

## Run the Server with SSL

The trace server can be run using SSL certificates. Jetty requires the certificate and private key to be in a keystore. Follow the instructions to [configure SSL on jetty](https://www.eclipse.org/jetty/documentation/current/configuring-ssl.html).

Then, you can edit the `tracecompass-server.ini` file to pass the keystore data and SSL port as parameters after the -vmargs line. For example, here's a extract of the file:

```
[...]
-vmargs
[...]]
-Dtraceserver.port=8443
-Dtraceserver.keystore=/path/to/keystore
```

The following properties are supported:

* `traceserver.port`: Port to use. If not specified, the default http port is 8080 and SSL is 8443
* `traceserver.useSSL`: Should be `true` or `false`. If `true`, the `traceserver.keystore` property must be set. If left unset, it will be inferred from the other properties.
* `traceserver.keystore`: Path to the keystore file.
* `traceserver.keystorepass`: Password to open the keystore file. If left unset, the password will be prompted when running the trace server application.
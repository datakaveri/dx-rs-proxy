[![Jenkins Build](https://img.shields.io/jenkins/build?jobUrl=https%3A%2F%2Fjenkins.iudx.io%2Fview%2Fiudx-master%2Fjob%2FIUDX%2520RS-Proxy%2520(master)%2520pipeline%2F&label=Build)](https://jenkins.iudx.io/view/iudx-master/job/IUDX%20RS-Proxy%20(master)%20pipeline/lastBuild/)
[![Jenkins Tests](https://img.shields.io/jenkins/tests?jobUrl=https%3A%2F%2Fjenkins.iudx.io%2Fview%2Fiudx-master%2Fjob%2FIUDX%2520RS-Proxy%2520(master)%2520pipeline%2F&label=Unit%20Test)](https://jenkins.iudx.io/view/iudx-master/job/IUDX%20RS-Proxy%20(master)%20pipeline/lastBuild/testReport/)
[![Jenkins Coverage](https://img.shields.io/jenkins/coverage/jacoco?jobUrl=https%3A%2F%2Fjenkins.iudx.io%2Fview%2Fiudx-master%2Fjob%2FIUDX%2520RS-Proxy%2520(master)%2520pipeline%2F&label=Coverage)](https://jenkins.iudx.io/view/iudx-master/job/IUDX%20RS-Proxy%20(master)%20pipeline/lastBuild/jacoco/)
[![Integration Tests](https://img.shields.io/jenkins/build?jobUrl=https%3A%2F%2Fjenkins.iudx.io%2Fview%2Fiudx-master%2Fjob%2FIUDX%2520RS-Proxy%2520(master)%2520pipeline%2F&label=Integration%20Tests)](https://jenkins.iudx.io/view/iudx-master/job/IUDX%20RS-Proxy%20(master)%20pipeline/lastBuild/Integration_20Test_20Report/)
[![Security Test](https://img.shields.io/jenkins/build?jobUrl=https%3A%2F%2Fjenkins.iudx.io%2Fview%2Fiudx-master%2Fjob%2FIUDX%2520RS-Proxy%2520(master)%2520pipeline%2F&label=Security%20Test)](https://jenkins.iudx.io/view/iudx-master/job/IUDX%20RS-Proxy%20(master)%20pipeline/lastBuild/zap/)

<p align="center">
<img src="./docs/cdpg.png" width="400">
</p>

# DX-resource-proxy-server(rs-proxy-server)

## Introduction

The DX Resource proxy server is Data Exchange(DX) data discovery portal.
It also facilitates data providers to register a connector to process the request and allows consumers to access data in
accordance with the provider's access policies.
The server ensures secure data access by integrating with an authorization server, requiring consumers to present access
tokens validated through token introspection APIs before serving protected data and supports advanced search
functionalities like temporal and geo-spatial queries through a data broker(RMQ) integration.
Consumers can access the data using HTTPs protocols.

<p align="center">
<img src="./docs/Resource Proxy Server Interactions.png">
</p>

## Features

- Provides data access from available resources using standard APIs.
- Search and count APIs for searching through available data: Support for Complex (Temporal + Attribute), Temporal (
  Before, during, After) and Attribute searches.
- Integration with authorization server (token introspection) to serve private data as per the access control policies
  set by the provider
- Automate connector registration process for data provider
- Integrates with AX Auditing server for logging and auditing the access for metering purposes
- Secure data access over TLS
- Scalable, service mesh architecture based implementation using open source components: Vert.X API framework, Postgres
  and RabbitMQ.
- Hazelcast and Zookeeper based cluster management and service discovery

# Explanation

## Understanding Rs Proxy Server

- The section available [here](./docs/Solution_Architecture.md) explains the components/services used in implementing
  the RS-proxy-server
- To try out the APIs, import the API collection, postman environment files in postman

Reference : [postman-collection](src/test/resources/IUDX-Resource-Proxy-Server-Consumer-APIs.postman_collection_5.5.0.json), [postman-environment](src/test/resources/Resource-Proxy-Server-Consumer-APIs.postman_environment.json)

# How To Guide

## Setup and Installation

Setup and Installation guide is available [here](./docs/SETUP-and-Installation.md)

# Reference

## API Docs

API docs are
available [here](https://redocly.github.io/redoc/?url=https://raw.githubusercontent.com/datakaveri/iudx-rs-proxy/master/docs/openapi.yaml)

## FAQ

FAQs are available [here](./docs/FAQ.md)

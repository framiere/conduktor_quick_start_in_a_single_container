# Conduktor Quick Start (Single Container)

This repo builds and runs a single Docker image that bundles Conduktor Console, Gateway, Redpanda, and supporting services.

> [!WARNING]
> DO NOT DO THIS AT HOME. 

## Build
```sh
docker build . -t conduktor_quick_start_in_a_single_container
```

## Run
```sh
docker run -d --name conduktor_quick_start_in_a_single_container \
  -p 8080:8080 \
  conduktor_quick_start_in_a_single_container
```

After the container starts, open http://localhost:8080 to access Conduktor Console.

## Demo 

[![asciicast](https://asciinema.org/a/OQsYnTz33wnUUMBU4bFJWIIR5.svg)](https://asciinema.org/a/OQsYnTz33wnUUMBU4bFJWIIR5)

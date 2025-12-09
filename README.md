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

  login `admin@demo.dev`
  password: `123=ABC_abc`

On the command line:

```sh
export CDK_USER=admin@demo.dev
export CDK_PASSWORD=123=ABC_abc
export CDK_BASE_URL=http://localhost:8080/
conduktor token create admin myToken
```


## Demo 

[![asciicast](https://asciinema.org/a/wZOS7MG8NJsySqskvkbLszDiJ.svg)](https://asciinema.org/a/wZOS7MG8NJsySqskvkbLszDiJ)

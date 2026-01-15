{{/*
Expand the name of the chart.
*/}}
{{- define "messaging-operator.name" -}}
{{- default .Chart.Name .Values.nameOverride | trunc 63 | trimSuffix "-" }}
{{- end }}

{{/*
Create a default fully qualified app name.
We truncate at 63 chars because some Kubernetes name fields are limited to this (by the DNS naming spec).
*/}}
{{- define "messaging-operator.fullname" -}}
{{- if .Values.fullnameOverride }}
{{- .Values.fullnameOverride | trunc 63 | trimSuffix "-" }}
{{- else }}
{{- $name := default .Chart.Name .Values.nameOverride }}
{{- if contains $name .Release.Name }}
{{- .Release.Name | trunc 63 | trimSuffix "-" }}
{{- else }}
{{- printf "%s-%s" .Release.Name $name | trunc 63 | trimSuffix "-" }}
{{- end }}
{{- end }}
{{- end }}

{{/*
Create chart name and version as used by the chart label.
*/}}
{{- define "messaging-operator.chart" -}}
{{- printf "%s-%s" .Chart.Name .Chart.Version | replace "+" "_" | trunc 63 | trimSuffix "-" }}
{{- end }}

{{/*
Common labels
*/}}
{{- define "messaging-operator.labels" -}}
helm.sh/chart: {{ include "messaging-operator.chart" . }}
{{ include "messaging-operator.selectorLabels" . }}
{{- if .Chart.AppVersion }}
app.kubernetes.io/version: {{ .Chart.AppVersion | quote }}
{{- end }}
app.kubernetes.io/managed-by: {{ .Release.Service }}
{{- end }}

{{/*
Selector labels
*/}}
{{- define "messaging-operator.selectorLabels" -}}
app.kubernetes.io/name: {{ include "messaging-operator.name" . }}
app.kubernetes.io/instance: {{ .Release.Name }}
{{- end }}

{{/*
Webhook service name
*/}}
{{- define "messaging-operator.webhookServiceName" -}}
{{- printf "%s-webhook" (include "messaging-operator.fullname" .) }}
{{- end }}

{{/*
Namespace helper - uses .Values.namespace or .Release.Namespace
*/}}
{{- define "messaging-operator.namespace" -}}
{{- default .Release.Namespace .Values.namespace }}
{{- end }}

{{/*
Webhook service FQDN for TLS certificate SAN
*/}}
{{- define "messaging-operator.webhookServiceFQDN" -}}
{{- $serviceName := include "messaging-operator.webhookServiceName" . -}}
{{- $namespace := include "messaging-operator.namespace" . -}}
{{- printf "%s.%s.svc" $serviceName $namespace }}
{{- end }}

{{/*
Webhook service full FQDN including cluster.local
*/}}
{{- define "messaging-operator.webhookServiceFullFQDN" -}}
{{- $serviceName := include "messaging-operator.webhookServiceName" . -}}
{{- $namespace := include "messaging-operator.namespace" . -}}
{{- printf "%s.%s.svc.cluster.local" $serviceName $namespace }}
{{- end }}

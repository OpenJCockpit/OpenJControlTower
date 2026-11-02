# Open Policy Agent with OpenJControlTower's standard policies. Policies and their data are part of the
# image (policy as code); the REST API is read-only through policies/system/authz.rego.
FROM openpolicyagent/opa:1.10.1-static
COPY policies /policies
USER 65532:65532
EXPOSE 8181
ENTRYPOINT ["/opa"]
CMD ["run", "--server", "--addr=0.0.0.0:8181", "--authorization=basic", "--ignore=*_test.rego", \
     "--disable-telemetry", "--log-format=json", "--log-level=info", "/policies"]

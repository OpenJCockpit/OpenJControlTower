# Authorizes requests to the agent's own REST API (opa run --authorization=basic).
# OpenJControlTower only reads: health, the loaded policies, and decisions. Nobody can replace a
# policy or its data through the API; change them in this directory and redeploy instead.
package system.authz

default allow := false

allow if input.method == "GET"

# Decisions with input: POST /v1/data/<decision path>
allow if {
	input.method == "POST"
	input.path[0] == "v1"
	input.path[1] == "data"
}

package system.authz_test

import data.system.authz

test_reads_and_decisions_are_allowed if {
	authz.allow with input as {"method": "GET", "path": ["health"]}
	authz.allow with input as {"method": "GET", "path": ["v1", "policies"]}
	authz.allow with input as {"method": "POST", "path": ["v1", "data", "openjcontroltower", "decision"]}
}

test_changing_policies_or_data_is_denied if {
	not authz.allow with input as {"method": "PUT", "path": ["v1", "policies", "decision.rego"]}
	not authz.allow with input as {"method": "PUT", "path": ["v1", "data", "openjcontroltower", "config"]}
	not authz.allow with input as {"method": "PATCH", "path": ["v1", "data"]}
	not authz.allow with input as {"method": "DELETE", "path": ["v1", "policies", "decision.rego"]}
	not authz.allow with input as {"method": "POST", "path": ["v1", "query"]}
}

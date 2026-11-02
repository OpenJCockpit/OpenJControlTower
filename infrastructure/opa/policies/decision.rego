# Decision document that OpenJControlTower asks for every governed action:
#   POST /v1/data/openjcontroltower/decision  {"input": {"action", "user": {"name", "roles"}, "resource"}}
# The answer is {"allow": bool, "deny": [reasons]}. A rule denies by adding a reason to "deny";
# an action is allowed when no rule denies it.
#
# Actions: repository.save, repository.check, spec.approve, queue.dispatch.
# Tunable values live in openjcontroltower/config/data.json (data.openjcontroltower.config).
package openjcontroltower.decision

import data.openjcontroltower.config

default allow := false

allow if count(deny) == 0

# Defence in depth: every governed action needs a role that may change things, even if the
# backend's own role check is misconfigured.
deny contains "You need one of the roles allowed to make changes in OpenJControlTower" if {
	not has_any_role(config.writer_roles)
}

has_any_role(roles) if {
	some role in input.user.roles
	role in roles
}

package openjcontroltower.decision

import data.openjcontroltower.config

# Repositories may only point at approved Git hosts, so credentials and spec content never go to
# an unknown server. This also limits where the connection check may send requests.
# An empty allowed_git_hosts list allows every host.
deny contains msg if {
	input.action in {"repository.save", "repository.check"}
	count(config.allowed_git_hosts) > 0
	not lower(input.resource.host) in {lower(host) | some host in config.allowed_git_hosts}
	msg := sprintf("Git host %s is not on the list of allowed Git hosts", [input.resource.host])
}

package openjcontroltower.decision

import data.openjcontroltower.config

# Four-eyes principle: whoever generated a spec may not approve it (when require_four_eyes is true).
deny contains "The person who generated this spec may not approve it (four-eyes principle)" if {
	input.action == "spec.approve"
	config.require_four_eyes
	input.resource.createdBy == input.user.name
}

# Every spec must be testable: it needs a minimum number of acceptance criteria ("- [ ] ..." items).
deny contains msg if {
	input.action == "spec.approve"
	count(input.resource.acceptanceCriteria) < config.min_acceptance_criteria
	msg := sprintf(
		"A spec needs at least %d acceptance criteria before approval; this one has %d",
		[config.min_acceptance_criteria, count(input.resource.acceptanceCriteria)],
	)
}

# Approval pushes the spec to Git: block content that looks like a private key or access token.
deny contains "The spec contains what looks like a private key or access token; remove it before approval" if {
	input.action == "spec.approve"
	some pattern in [
		`-----BEGIN [A-Z ]*PRIVATE KEY-----`, # PEM private keys
		`AKIA[0-9A-Z]{16}`, # AWS access key IDs
		`gh[pousr]_[A-Za-z0-9]{36,}`, # GitHub tokens
		`glpat-[A-Za-z0-9_-]{20,}`, # GitLab personal access tokens
		`xox[abprs]-[A-Za-z0-9-]{10,}`, # Slack tokens
	]
	regex.match(pattern, input.resource.body)
}

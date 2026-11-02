package openjcontroltower.decision_test

import data.openjcontroltower.decision

config := {
	"writer_roles": ["controltower-editor", "controltower-admin"],
	"allowed_git_hosts": ["git.example.org"],
	"require_four_eyes": true,
	"min_acceptance_criteria": 2,
	"dispatch_freeze": false,
	"dispatch_roles": ["controltower-admin"],
}

editor := {"name": "alice", "roles": ["controltower-editor"]}

admin := {"name": "bob", "roles": ["controltower-admin"]}

spec(body, created_by, criteria) := {"body": body, "createdBy": created_by, "acceptanceCriteria": criteria}

test_allows_repository_on_allowed_host if {
	decision.allow with input as {"action": "repository.save", "user": editor, "resource": {"host": "Git.Example.org"}}
		with data.openjcontroltower.config as config
}

test_denies_repository_on_other_host if {
	result := decision with input as {"action": "repository.check", "user": editor, "resource": {"host": "evil.example.net"}}
		with data.openjcontroltower.config as config
	not result.allow
	result.deny == {"Git host evil.example.net is not on the list of allowed Git hosts"}
}

test_empty_host_list_allows_every_host if {
	decision.allow with input as {"action": "repository.save", "user": editor, "resource": {"host": "anywhere.example"}}
		with data.openjcontroltower.config as object.union(config, {"allowed_git_hosts": []})
}

test_denies_users_without_a_writer_role if {
	result := decision with input as {"action": "repository.save", "user": {"name": "v", "roles": ["controltower-viewer"]}, "resource": {"host": "git.example.org"}}
		with data.openjcontroltower.config as config
	not result.allow
	"You need one of the roles allowed to make changes in OpenJControlTower" in result.deny
}

test_four_eyes_denies_the_author if {
	result := decision with input as {"action": "spec.approve", "user": editor, "resource": spec("ok", "alice", ["a", "b"])}
		with data.openjcontroltower.config as config
	result.deny == {"The person who generated this spec may not approve it (four-eyes principle)"}
}

test_four_eyes_allows_another_person if {
	decision.allow with input as {"action": "spec.approve", "user": admin, "resource": spec("ok", "alice", ["a", "b"])}
		with data.openjcontroltower.config as config
}

test_four_eyes_can_be_switched_off if {
	decision.allow with input as {"action": "spec.approve", "user": editor, "resource": spec("ok", "alice", ["a", "b"])}
		with data.openjcontroltower.config as object.union(config, {"require_four_eyes": false})
}

test_requires_acceptance_criteria if {
	result := decision with input as {"action": "spec.approve", "user": admin, "resource": spec("ok", "alice", ["only one"])}
		with data.openjcontroltower.config as config
	result.deny == {"A spec needs at least 2 acceptance criteria before approval; this one has 1"}
}

test_blocks_secrets_in_specs if {
	every body in [
		"-----BEGIN RSA PRIVATE KEY-----",
		"key AKIAABCDEFGHIJKLMNOP",
		concat("", ["ghp_", "abcdefghijklmnopqrstuvwxyz0123456789"]),
		"glpat-abcdefghij0123456789",
		"xoxb-1234567890-abc",
	] {
		result := decision with input as {"action": "spec.approve", "user": admin, "resource": spec(body, "alice", ["a", "b"])}
			with data.openjcontroltower.config as config
		result.deny == {"The spec contains what looks like a private key or access token; remove it before approval"}
	}
}

test_dispatch_requires_a_dispatch_role if {
	result := decision with input as {"action": "queue.dispatch", "user": editor, "resource": {"specIds": ["s1"]}}
		with data.openjcontroltower.config as config
	result.deny == {"Your role may not dispatch specs to OpenJCockpit"}
	decision.allow with input as {"action": "queue.dispatch", "user": admin, "resource": {"specIds": ["s1"]}}
		with data.openjcontroltower.config as config
}

test_dispatch_freeze_denies_everyone if {
	result := decision with input as {"action": "queue.dispatch", "user": admin, "resource": {"specIds": ["s1"]}}
		with data.openjcontroltower.config as object.union(config, {"dispatch_freeze": true})
	result.deny == {"Dispatch is frozen: no specs may be sent to OpenJCockpit right now"}
}

test_shipped_defaults_allow_the_development_flow if {
	decision.allow with input as {"action": "spec.approve", "user": editor, "resource": spec("ok", "alice", ["a", "b"])}
	decision.allow with input as {"action": "queue.dispatch", "user": editor, "resource": {"specIds": ["s1"]}}
	decision.allow with input as {"action": "repository.save", "user": editor, "resource": {"host": "github.com"}}
}

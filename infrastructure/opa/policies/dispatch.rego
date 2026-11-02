package openjcontroltower.decision

import data.openjcontroltower.config

# A dispatch freeze (for example during a release or an incident) stops all work going to OpenJCockpit.
deny contains "Dispatch is frozen: no specs may be sent to OpenJCockpit right now" if {
	input.action == "queue.dispatch"
	config.dispatch_freeze
}

# Only these roles may start work in OpenJCockpit.
deny contains "Your role may not dispatch specs to OpenJCockpit" if {
	input.action == "queue.dispatch"
	not has_any_role(config.dispatch_roles)
}

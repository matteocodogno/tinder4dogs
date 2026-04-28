package com.ai4dev.tinderfordogs.agent.tool

import org.springframework.stereotype.Component
import org.springframework.web.context.annotation.RequestScope

@Component
@RequestScope
class AgentContext {
    var referenceDogName: String = ""
    var referenceLatitude: Double = 0.0
    var referenceLongitude: Double = 0.0
    var preferredDate: String = "weekend"
}

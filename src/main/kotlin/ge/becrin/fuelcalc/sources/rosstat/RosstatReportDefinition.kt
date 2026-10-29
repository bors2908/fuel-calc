package ge.becrin.fuelcalc.sources.rosstat

import ge.becrin.fuelcalc.fuels.FuelType
import tools.jackson.databind.node.ObjectNode

data class RosstatReportDefinition(
    val config: ObjectNode,

    val regions: Map<Long, String>,

    val fuels: Map<Long, FuelType>
)

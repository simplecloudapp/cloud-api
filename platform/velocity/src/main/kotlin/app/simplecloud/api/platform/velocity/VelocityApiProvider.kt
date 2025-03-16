package app.simplecloud.api.platform.velocity

import org.slf4j.Logger
import javax.inject.Inject

class VelocityApiProvider @Inject constructor(logger: Logger) {
    init {
        logger.info("SimpleCloud v3 API provider initialized!")
    }
}
package com.kabirbhasin.statuscalendar.engine

import com.kabirbhasin.statuscalendar.core.format.RenderedDisplay

interface DisplayEngine {
    fun start()
    fun stop()
    fun render(display: RenderedDisplay)
}

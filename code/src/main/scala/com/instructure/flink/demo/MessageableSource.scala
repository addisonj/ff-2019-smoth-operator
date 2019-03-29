package com.instructure.flink.demo

import org.apache.flink.streaming.api.functions.source.SourceFunction.SourceContext
import org.apache.flink.streaming.api.operators.{
  AbstractStreamOperator,
  OneInputStreamOperator,
  StreamSourceContexts
}
import org.apache.flink.streaming.runtime.streamrecord.StreamRecord

abstract class MessageableSource[IN, OUT](idleTimeout: Long)
    extends AbstractStreamOperator[OUT]
    with OneInputStreamOperator[IN, OUT] {
  private var sourceCtx: SourceContext[OUT] = _
  protected def sourceContext: SourceContext[OUT] = {
    if (sourceCtx == null) {
      val timeCharacteristic = getOperatorConfig.getTimeCharacteristic
      val watermarkInterval  = getRuntimeContext.getExecutionConfig.getAutoWatermarkInterval
      val streamStatus       = getContainingTask.getStreamStatusMaintainer
      sourceCtx = StreamSourceContexts.getSourceContext(
        timeCharacteristic,
        getContainingTask.getProcessingTimeService,
        getContainingTask.getCheckpointLock,
        streamStatus,
        output,
        watermarkInterval,
        idleTimeout
      )
    }
    sourceCtx
  }
  override def processElement(element: StreamRecord[IN]): Unit =
    processElement(element.getValue, sourceContext)

  def processElement(el: IN, sourceCtx: SourceContext[OUT]): Unit
}

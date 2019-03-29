package com.instructure.flink.demo

import org.apache.flink.streaming.api.scala.StreamExecutionEnvironment
import org.apache.flink.api.scala._
import org.apache.flink.streaming.api.TimeCharacteristic
import org.apache.flink.streaming.api.functions.source.SourceFunction
import org.apache.flink.streaming.api.windowing.assigners.TumblingEventTimeWindows
import org.apache.flink.streaming.api.windowing.time.Time

class FileReaderSource extends MessageableSource[FileReadRequest, FileMessage](1000L) {
  override def processElement(el: FileReadRequest,
                              sourceCtx: SourceFunction.SourceContext[FileMessage]): Unit = ???
}

object FileReaderDemo2 {

  def main(args: Array[String]): Unit = {
    val env = StreamExecutionEnvironment.getExecutionEnvironment
    env.setStreamTimeCharacteristic(TimeCharacteristic.EventTime)
    val fileStream =
      env.addSource(new FileRequestSource)
    val messageStream = fileStream
      .transform("fileReader", new FileReaderSource)
      .setParallelism(25)
      .assignTimestampsAndWatermarks(new WatermarkAssigner())

    val outStream = messageStream
      .keyBy("id")
      .window(TumblingEventTimeWindows.of(Time.hours(1)))
      .sum("value")
      .setParallelism(50)
    outStream.print()
    env.execute()
  }

}

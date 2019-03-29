package com.instructure.flink.demo

import org.apache.flink.streaming.api.functions.source.SourceFunction
import org.apache.flink.streaming.api.scala.StreamExecutionEnvironment
import org.apache.flink.api.scala._
import org.apache.flink.streaming.api.TimeCharacteristic
import org.apache.flink.streaming.api.functions.{AssignerWithPeriodicWatermarks, ProcessFunction}
import org.apache.flink.streaming.api.watermark.Watermark
import org.apache.flink.streaming.api.windowing.assigners.TumblingEventTimeWindows
import org.apache.flink.streaming.api.windowing.time.Time
import org.apache.flink.util.Collector

case class FileReadRequest(uri: String)
case class FileMessage(sourceUri: String, timestamp: Long, id: String, value: Long)

class FileRequestSource extends SourceFunction[FileReadRequest] {
  override def run(ctx: SourceFunction.SourceContext[FileReadRequest]): Unit = ???
  override def cancel(): Unit                                                = ???
}

class FileReaderProcess extends ProcessFunction[FileReadRequest, FileMessage] {
  override def processElement(value: FileReadRequest,
                              ctx: ProcessFunction[FileReadRequest, FileMessage]#Context,
                              out: Collector[FileMessage]): Unit = {
    processFile(value, out)
  }

  def processFile(el: FileReadRequest, out: Collector[FileMessage]): Unit = ???
}

class WatermarkAssigner extends AssignerWithPeriodicWatermarks[FileMessage] {
  override def getCurrentWatermark: Watermark = ???

  override def extractTimestamp(element: FileMessage, previousElementTimestamp: Long): Long =
    ???
}

object FileReaderDemo {

  def main(args: Array[String]): Unit = {
    val env = StreamExecutionEnvironment.getExecutionEnvironment
    env.setStreamTimeCharacteristic(TimeCharacteristic.EventTime)
    val fileStream =
      env.addSource(new FileRequestSource)
    val messageStream = fileStream
      .process(new FileReaderProcess)
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

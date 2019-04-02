
<h1 class="mainTitle">Becoming a Smooth Operator</h1>
<h2 class="mainTitle">A look at the StreamOperator API</h2>

---

## Who am I?

----

### Addison Higham
<img width="200" class="plain" src="./images/me.jpg" alt="Addison Higham" style="vertical-align: middle"/>

- Cloud/Data Guy at Instructure
- github.com/addisonj
- twitter/addisonjh

---

## How I knew Flink was great

----

- Technology <!-- .element: class="fragment strike" data-fragment-index="1" -->
- Community <!-- .element: class="fragment strike" data-fragment-index="2" -->
- Best Apache Animal Logo <!-- .element: class="fragment fade-in" data-fragment-index="3" -->

Note: Started with flink over 3 years ago

----

<img width="600" class="plain" src="./images/hadoop.png" alt="Hadoop" style="vertical-align: middle"/>

### Simple, but okay

----

<img width="600" class="plain" src="./images/pig.png" alt="Pig" style="vertical-align: middle"/>

### Off-brand Mario

----

<img width="400" class="plain" src="./images/hive.png" alt="Hive" style="vertical-align: middle"/>

### Nightmare Fuel

----

<img width="600" class="plain" src="./images/flink-1.png" alt="Flink" style="vertical-align: middle"/>

### Clearly the winner

---

<img width="600" class="plain" src="./images/inst-logo.svg" alt="Instructure" style="vertical-align: middle"/>

### From the first day of school to the last day of work

----

<div style="display: flex; justify-context: space-evenly;">
  <div>
    <img width="200" class="plain" src="./images/canvas-logo.svg" alt="Canvas" style="vertical-align: middle"/>
    <p style="vertical-align: middle">Learning Management System</p>
  </div>
  <div>
    <img width="200" class="plain" src="./images/bridge-logo.svg" alt="Bridge" style="vertical-align: middle"/>
    <p style="vertical-align: middle">Employment Development</p>
  </div>
</div>

----

### How We Use Flink

----

### Get more data to customers faster

- Fast and correct ETLs
- Replace/Augment batch systems
- Still new effort, but growing

----

### Replacement For Lambda Archictures
#### We found Lambda to be:

- Difficult to maintain (two stacks, duplicated logic) <!-- .element: class="fragment" data-fragment-index="1" -->
- Hard to coordinate (external state, source of truth) <!-- .element: class="fragment" data-fragment-index="2" -->
- Hard to debug (erroneous or missing data, bad UX) <!-- .element: class="fragment" data-fragment-index="3" -->
- Not worth doing anymore (with better stream processors, why use lambda?) <!-- .element: class="fragment" data-fragment-index="4" -->

----

### Our experience so far

Flink is a feature-rich and fast stream processor with flexibility to meet a ton of different demands

The `StreamOperator` API is one aspect that leads to Flink's flexibility

---

## What we will cover today

----

### The `StreamOperator` API

- What it is and how it fits in with the other Flink APIs
- What it enables and how Flink uses it
- Some examples of what advanced functionality you can do with it
- How Instructure uses it to move to Kappa-like Architectures

---

## The Many Layers of Flink

----

<img width="600" class="plain" src="./images/stack.png" alt="Flink API Layers" style="vertical-align: middle"/>

Note: you have probably seen this before, but as a review, lets look in depth at some of those layers

----

### Flink SQL

<pre><code class="lang-Scala" style="font-size: 0.7em; line-height: 1.2em" data-trim data-noescape>
case class Student(id: Long, name: String)
case class Grade(id: Long, assignmentId: Long, studentId: Long, score: Int)
...
val students = env.fromCollection(Seq(
  Student(1, "Jane"),
  Student(2, "Bill"),
  Student(3, "Addison")
))
val grades = env.fromCollection(Seq(
  Grade(1, 1, 1, 85),
  Grade(2, 1, 2, 79),
  Grade(3, 1, 3, 42),
  Grade(4, 1, 3, 42)
))
<span class="fragment" data-fragment-index=2>val tableEnv = TableEnvironment.getTableEnvironment(env)
tableEnv.registerDataStream("Students", students)
tableEnv.registerDataStream("Grades", grades)</span>
<span class="fragment" data-fragment-index=3>val fails = tableEnv.sqlQuery("""
SELECT * FROM Grades INNER JOIN Students ON Grades.studentId = Students.id
WHERE Grades.score &lt; 60
""")
</span>
</code></pre>

Note: lowest common denominator, easy to understand and relatively flexible, but you can't express everything

----

### "High Level" Stream APIs (map, filter, window, etc)

<pre><code class="lang-Scala" style="font-size: 0.7em; line-height: 1.2em" data-trim data-noescape>
case class Grade(id: Long, assignmentId: Long, studentId: Long, score: Int, attempt: Int)
val grades = env.fromCollection(Seq(
  Grade(1, 1, 1, 85, 1),
  Grade(2, 1, 2, 79, 1),
  Grade(3, 1, 3, 42, 1),
  Grade(4, 1, 3, 42, 2)
))

<span class="fragment" data-fragment-index=1>val avgFirstsAttempt = grades
  .keyBy("assignmentId")
  // keep only first attempts
  .filter((grade) => grade.attempt == 0)</span>
<span class="fragment" data-fragment-index=2>  // just store everything in one window
  .window(GlobalWindows.create())
  // a trigger that fires every n seconds
  .trigger(new PeriodicFiringTrigger())
  // an aggregate function to keep a running average
  .aggregate(new AverageAggregate())</span>
</code></pre>

Note: They expose a lot more functionality (precise control over graph, custom state, etc), but comes at a cost and a lot is still hidden

----

### "Low Level" Stream APIs (process, async, broadcast, etc)

<pre><code class="lang-Scala" style="font-size: 0.5em; line-height: 1.2em" data-trim data-noescape>
case class Grade(id: Long, assignmentId: Long, studentId: Long, score: Int, attempt: Int, dueAt: Instant)
case class NotifyFail(studentId: Long, message: String)
val grades = env.fromCollection(Seq(
  Grade(1, 1, 1, 85, 1, Instant.parse("2019-04-01T23:50:00Z")),
  Grade(2, 1, 2, 79, 1, Instant.parse("2019-04-01T23:50:00Z")),
  Grade(3, 1, 3, 42, 1, Instant.parse("2019-04-01T23:50:00Z")),
  Grade(4, 1, 3, 42, 2, Instant.parse("2019-04-01T23:50:00Z"))
))

<span class="fragment" data-fragment-index=1>val notifyFailingGrades = grades.
  keyBy("assignmentId", "studentId")
  process(new KeyedProcessFunction[Grade, NotifyFail] with RichFunction {
    private val WarnTime = 1000 * 60 * 60 // one hour
    private val WarnScore = 60 // warn if score is failing
    lazy private val bestGrade: ValueState[Grade] = getRuntimeContext().getState("bestGrade", ...)
    override def processElement(el: Grade, ctx: Context, coll: Collector[NotifyFail]): Unit = {
      if (bestGrade.get == null) {
        val warnAt = el.dueAt.toEpochMilli() - WarnTime
        ctx.timerService.registerProcessingTimeTimer(warnAt)
      }
      if (el.score &gt; Option(bestGrade.get()).map(_.score).getOrElse(-1)) {
        bestGrade.update(el)
      }
    }</span>
<span class="fragment" data-fragment-index=2>    override def onTimer(ts Long, ctx: OnTimerContext, coll: Collector[NotifyFail]): Unit = {
      val grade = bestGrade.get()
      if (grade.score &lt; WarnScore) {
        coll.collect(NotifyFail(grade.studentId, s"You are failing assignment ${grade.assignmentId}"))
      }
    }
  })</span>
</code></pre>

Note: We get even more control, timers, multiple outputs, keys

----

<img width="800" class="plain" src="./images/api-stack.png" alt="Flink API Layers" style="vertical-align: middle"/>

----

### Is that all the control we get?

- checkpoint barriers, watermarks, latency marker handling?
- state snapshotting?
- per-key state magic?
- ... and a whole lot more?

----

### Public vs Private APIs?

<img width="600" class="plain" src="./images/stack-split.png" alt="Flink API Layers" style="vertical-align: middle"/>

Note: Does flink expose just a few APIs and the rest is "private" not to be touched by us?

----

### Flink's API Design Approach

<div style="font-size: 0.5em"><em>In my opinion</em></div>

- Flink marks very few classes/interfaces as private to allow users lots of flexibility
- Instead, guidelines are given via annotations and docs
- This encourages users to experiment and understand, without ossification or constant breakage
- As Flink evolves, more functionality is moved into higher level APIs

Note: public/total files in biggest modules: flink-streaming-java (345/355), flink-core (642/649), flink-runtime (1420/1501), by comparision, spark-core (222/949) classes are public

----

<img width="600" class="plain" src="./images/stack-expanded.png" alt="Flink API Expanded" style="vertical-align: middle"/>

---

## `StreamOperator` API

----

### Warning

- The `StreamOperator` is marked as `PublicEvolving`, which means that it can
change between versions.
- Also, you can totally break your app and do weird things <!-- .element: class="fragment highlight-red" data-fragment-index="1" -->
- Really, you should only use this if you know why<!-- .element: class="fragment" data-fragment-index="2" -->

Note: some examples, you can break watermarks really easily, you can make checkpointing really slow, you can

----

### What it does

- is the primary building block of the `DataStream` API
- encapsulates each `DataStream` operation
- handles interaction with runtime and internal APIs
- handle internal messaging (`StreamRecord`, `Watermark`, etc)
- facilitates state snapshotting and managing key contexts

----

### What it looks like
<pre><code style="max-height: 550px; font-size: 0.7em; line-height: 1.2em" class="java" data-trim data-noescape>
// StreamOperator.java
@PublicEvolving
public interface StreamOperator&lt;OUT&gt; extends CheckpointListener, KeyContext, Disposable, Serializable {
<span class="fragment" data-fragment-index=1>  // lifecycle
  void setup(StreamTask&lt;?, ?&gt; containingTask, StreamConfig config, Output&lt;StreamRecord&lt;OUT&gt;&gt; output);
  void open() throws Exception;
  void close() throws Exception;
  void dispose() throws Exception;</span>

<span class="fragment" data-fragment-index=2>  // state
  void prepareSnapshotPreBarrier(long checkpointId) throws Exception;
  OperatorSnapshotFutures snapshotState(...) throws Exception;
  void initializeState() throws Exception;
  // from CheckpointListener interface
  // void notifyCheckpointComplete(long checkpointId) throws Exception;</span>

<span class="fragment" data-fragment-index=3>  // keys
  // from KeyContext interface
  // void setCurrentKey(Object key);
  void setKeyContextElement1(StreamRecord&lt;?&gt; record) throws Exception;
  void setKeyContextElement2(StreamRecord&lt;?&gt; record) throws Exception;
  ...</span>
}
</code></pre>

----

### What it looks like (continued)

<pre><code style="max-height: 550px; font-size: 0.7em; line-height: 1.2em" class="java" data-trim data-noescape>
// OneInputStreamOperator.java
@PublicEvolving
public interface OneInputStreamOperator&lt;IN, OUT&gt; extends StreamOperator&lt;OUT&gt; {
  // process messages
  void processElement(StreamRecord&lt;IN&gt; element) throws Exception;
  void processWatermark(Watermark mark) throws Exception;
  void processLatencyMarker(LatencyMarker latencyMarker) throws Exception;
}
</code></pre>

or

<pre><code style="max-height: 550px; font-size: 0.7em; line-height: 1.2em" class="java" data-trim data-noescape>
// TwoInputStreamOperator.java
@PublicEvolving
public interface TwoInputStreamOperator&lt;IN1, IN2, OUT&gt; extends StreamOperator&lt;OUT&gt; {
  // process messages x2
  void processElement1(StreamRecord&lt;IN1&gt; element) throws Exception;
  void processElement2(StreamRecord&lt;IN2&gt; element) throws Exception;
  void processWatermark1(Watermark mark) throws Exception;
  void processWatermark2(Watermark mark) throws Exception;
  void processLatencyMarker1(LatencyMarker latencyMarker) throws Exception;
  void processLatencyMarker2(LatencyMarker latencyMarker) throws Exception;
}
</code></pre>

----

### Some things look familiar...

We still have methods for:
- hooking into life-cycle
- dealing with state
- processing messages

----

### However, we see a whole lot more

<ul style="font-size: 0.7em">
  <li><code class="small">void setup(StreamTask<?, ?>, StreamConfig, Output<StreamRecord<OUT>>)</code></li>
  <li><code class="small">OperatotSnapshotFuture snapshotState(...)</code></li>
  <li>Handlers for <code class="small">StreamRecord</code>, <code class="small">Watermark</code> and other messages</li>
  <li>dealing with keys</li>
</ul>

----

### And some things aren't so clear...

- How do we send messages downstream?
- How do we perform a snapshot?
- How do we set timers?

----

### A Helping Hand

<p class="small">Luckily, we don't have to figure this all out, the `AbstractStreamOperator` gives us some help
in using this API</p>


<pre><code style="max-height: 550px; font-size: 0.9em; line-height: 1.2em" class="scala" data-trim data-noescape>
class MyFirstOperator
    extends AbstractStreamOperator[String]
    with OneInputStreamOperator[String, String] {

  <span class="fragment" data-fragment-index=1>override def processElement(element: StreamRecord[String]): Unit = {
    output.collect(element.replace(element.getValue + "!!!!"))
  }</span>
}
</code></pre>

----

<img width="400" class="plain" src="./images/confused.gif" alt="Confused" style="vertical-align: middle"/>

### ... That's it?
#### Why would I want to use this?

---

## How Flink uses it

----

#### `TimestampsAndPeriodicWatermarksOperator`

----

### What does it do?

<p class="small">When you use any watermark extractor, it gets wrapped in a <code>TimestampsAndPeriodicWatermarksOperator</code></p>

<pre><code style="max-height: 550px; font-size: 0.7em; line-height: 1.2em" class="java" data-trim data-noescape>
class MyWatermarkExtractor extends AssignerWithPeriodicWatermarks[MyEvent] {
  private var maxTimestamp: Long = 0L
  override def extractTimestamp(element: MyEvent, previousElementTimestamp: Long): Long = {
    if (element.timestamp &gt; maxTimestamp) {
      maxTimestamp = element.timestamp
    }
    element.timestamp
  }
  override def getCurrentWatermark(): Watermark = new Watermark(maxTimestamp - 1)
}

dataStream.assignTimestampsAndWatermarks(new MyWatermarkExtractor)
</code></pre>


----

### The Code

<pre><code style="max-height: 550px; font-size: 0.7em; line-height: 1.2em" class="java" data-trim data-noescape>
public class TimestampsAndPeriodicWatermarksOperator&lt;T&gt;
		extends AbstractUdfStreamOperator&lt;T, AssignerWithPeriodicWatermarks&lt;T&gt;&gt;
		implements OneInputStreamOperator&lt;T, T&gt;, ProcessingTimeCallback {
	private transient long watermarkInterval;
	private transient long currentWatermark;

	public TimestampsAndPeriodicWatermarksOperator(AssignerWithPeriodicWatermarks&lt;T&gt; assigner) {
		super(assigner);
		<span class="fragment highlight-red" data-fragment-index=1>this.chainingStrategy = ChainingStrategy.ALWAYS;</span>
	}
	@Override
	public void open() throws Exception {
		super.open();

		currentWatermark = Long.MIN_VALUE;
		<span class="fragment highlight-red" data-fragment-index=2>watermarkInterval = getExecutionConfig().getAutoWatermarkInterval();</span>

		if (watermarkInterval &gt; 0) {
			<span class="fragment highlight-red" data-fragment-index=3>long now = getProcessingTimeService().getCurrentProcessingTime();
			getProcessingTimeService().registerTimer(now + watermarkInterval, this);</span>
		}
	}</span>
  @Override
	public void processElement(StreamRecord&lt;T&gt; element) throws Exception {
		final long newTimestamp = userFunction.extractTimestamp(element.getValue(),
				element.hasTimestamp() ? element.getTimestamp() : Long.MIN_VALUE);

		<span class="fragment highlight-red" data-fragment-index=4>output.collect(element.replace(element.getValue(), newTimestamp));</span>
	}
</code></pre>

----

### The Code (continued)

<pre><code style="max-height: 550px; font-size: 0.7em; line-height: 1.2em" class="java" data-trim data-noescape>
  <span class="fragment semi-fade-out" data-fragment-index=2>@Override
  public void onProcessingTime(long timestamp) throws Exception {
    // register next timer
    Watermark newWatermark = userFunction.getCurrentWatermark();
    if (newWatermark != null && newWatermark.getTimestamp() &gt; currentWatermark) {
      currentWatermark = newWatermark.getTimestamp();
      // emit watermark</span>
      <span class="fragment highlight-red" data-fragment-index=1>output.emitWatermark(newWatermark);</span>
    <span class="fragment semi-fade-out" data-fragment-index=2>}

    long now = getProcessingTimeService().getCurrentProcessingTime();
    getProcessingTimeService().registerTimer(now + watermarkInterval, this);
  }</span>

  /**
   * Override the base implementation to completely ignore watermarks propagated from
   * upstream (we rely only on the {@link AssignerWithPunctuatedWatermarks} to emit
   * watermarks from here).
   */
  <span class="fragment semi-fade-out" data-fragment-index=2>@Override
  public void processWatermark(Watermark mark) throws Exception {
    // if we receive a Long.MAX_VALUE watermark we forward it since it is used
    // to signal the end of input and to not block watermark progress downstream
    if (mark.getTimestamp() == Long.MAX_VALUE && currentWatermark != Long.MAX_VALUE) {
      currentWatermark = Long.MAX_VALUE;
      output.emitWatermark(mark);
    }
  }</span>
  ...
}
</code></pre>

----

### What we learn

- In `processElement` we get the `StreamRecord` which allows us to modify not only the record, but it's timestamp
- In `processWatermark` we can change or completely ignore watermarks
- We have access to lots of APIs, such as timer services, `output` and `chainingStrategy` properties

----

### The `StreamOperator` pattern

<p class="small">As mentioned, all of the `DataStream` API is built on top of and encapsulated by the `StreamOperator` API</p>

<pre class="fragment" data-fragment-index=1><code style="max-height: 550px; font-size: 0.7em; line-height: 1.2em" class="java" data-trim data-noescape>
public &lt;R&gt; SingleOutputStreamOperator&lt;R&gt; map(MapFunction&lt;T, R&gt; mapper) {
  TypeInformation&lt;R&gt; outType = ...;
  return transform("Map", outType, new StreamMap&lt;&gt;(clean(mapper)));
}
</code></pre>

<img width="400" class="plain fragment" src="./images/stream_map.png" alt="Stream Map UML" style="vertical-align: middle" data-fragment-index=2/>

---

## Our First Real Operator

----

### Handling a stream of files

#### We have a job that:

- has a single source which produces `FileReadRequest` messages, can send anywhere from 150 to 0.1 msgs/minute
- a process function which reads the file and produces roughly ~100k messages per file, these new messages contain our event-time <!-- .element: class="fragment" data-fragment-index="1" -->
- downstream logic that re-keys, aggregates, and sums into 1 hour windows <!-- .element: class="fragment" data-fragment-index="2" -->

----

### In Code

<pre><code style="max-height: 550px; font-size: 0.7em; line-height: 1.2em" class="scala" data-trim data-noescape>
// our message types
case class FileReadRequest(uri: String)
case class FileMessage(sourceUri: String, timestamp: Long, id: String, value: Long)
<span class="fragment" data-fragment-index=1>// our functions
class FileRequestSource extends SourceFunction[FileReadRequest] {...}
class FileReaderProcess extends ProcessFunction[FileReadRequest, FileMessage] {...}
class WatermarkAssigner extends AssignerWithPeriodicWatermarks[FileMessage] {...}</span>
<span class="fragment" data-fragment-index=2>// our job
class FileReaderDemo {
  def main(args: Array[String]): Unit = {
    val env = StreamExecutionEnvironment.getExecutionEnvironment
    env.setStreamTimeCharacteristic(TimeCharacteristic.EventTime)
    val fileStream    = env.addSource(new FileRequestSource)
    val messageStream = fileStream
      .process(new FileReaderProcess)
      .setParallelism(25)
      .assignTimestampsAndWatermarks(new WatermarkAssigner)</span>

    <span class="fragment" data-fragment-index=3>messageStream
      .keyBy("id")
      .window(TumblingEventTimeWindows.of(Time.hours(1)))
      .sum("value")
      .setParallelism(50)
      .addSink(...)
  }
}</span>
</code></pre>

----

### Job Graph

<img width="800" class="plain" src="./images/file-graph-1.png" alt="File Graph" style="vertical-align: middle"/>

----

### Problems Arise

At low load, we start seeing no windows closing until load picks back up

Analysis shows the watermark isn't advancing

----

### Root Cause

- At low load, we get roughly 6 msgs/hour
- In an hour, only ~25% of readers will have a message to process, the rest will be idle <!-- .element: class="fragment" data-fragment-index="1" -->
- Since we assign watermarks after our reader, earlier watermarks are discarded <!-- .element: class="fragment" data-fragment-index="2" -->

----
### Dealing with idle streams

<img width="400" class="plain" src="./images/parallel_streams_watermarks.svg" alt="Watermarks" style="vertical-align: middle"/>

<p class="small">The watermark for an operator is the minimum of all the input watermarks, when we have a single "source" that doesn't output any records, the watermark doesn't advance</p>

<p class="small">Without an advancing watermark, we can't close windows, and we stop getting results</p>

----

### How to fix it!

> Sources can be marked as idle using SourceFunction.SourceContext #markAsTemporarilyIdle

*From https://ci.apache.org/projects/flink/flink-docs-stable/dev/event_time.html#idling-sources*

----

<img width="600" class="plain" src="./images/sad.gif" alt="Sad" style="vertical-align: middle"/>

#### Except... our source isn't what is idle...

----

### What is a source really?

In this case, the real "source" of most of our data is **not** the SourceFunction

With the operator API, we can work around this API limitation

Note: there are other options to fixing this, such as a different watermark generation scheme,
but that still is a workaround when the problem is that the real bulk of our data isn't produced by the source

----

### Our new abstraction

<pre><code style="max-height: 550px; font-size: 0.7em; line-height: 1.2em" class="scala" data-trim data-noescape>
abstract class MessageableSource[IN, OUT](idleTimeout: Long)
    extends AbstractStreamOperator[OUT]
    with OneInputStreamOperator[IN, OUT] {
  <span class="fragment" data-fragment-index=1>private var sourceCtx: SourceContext[OUT] = _
  protected def sourceContext: SourceContext[OUT] = {
    if (sourceCtx == null) {
      sourceCtx = StreamSourceContexts.getSourceContext(
        getOperatorConfig.getTimeCharacteristic,
        getContainingTask.getProcessingTimeService,
        getContainingTask.getCheckpointLock,
        getContainingTask.getStreamStatusMaintainer,
        output,
        getRuntimeContext.getExecutionConfig.getAutoWatermarkInterval,
        idleTimeout
      )
    }
    sourceCtx
  }</span>

  <span class="fragment" data-fragment-index=2>def processElement(el: IN, sourceCtx: SourceContext[OUT]): Unit</span>
  <span class="fragment" data-fragment-index=3>override def processElement(element: StreamRecord[IN]): Unit =
    processElement(element.getValue, sourceContext)</span>
}
</code></pre>


----

### Used in our new job

<pre><code style="max-height: 550px; font-size: 0.7em; line-height: 1.2em" class="scala" data-trim data-noescape>
<span class="fragment semi-fade-out" data-fragment-index=1>// our functions
class FileRequestSource extends SourceFunction[FileReadRequest] {...}</span>
class FileReaderSource extends MessageableSource[FileReadRequest, FileMessage](idleTimeout) {...}
<span class="fragment semi-fade-out" data-fragment-index=1>class WatermarkAssigner extends AssignerWithPeriodicWatermarks[FileMessage] {...}
// our message types
case class FileReadRequest(uri: String)
case class FileMessage(sourceUri: String, timestamp: Long, id: String, value: Long)
// our job
class FileReaderDemo {
  def main(args: Array[String]): Unit = {
    val env = StreamExecutionEnvironment.getExecutionEnvironment
    env.setStreamTimeCharacteristic(TimeCharacteristic.EventTime)
    val fileStream    = env.addSource(new FileRequestSource)
    val messageStream = fileStream</span>
      .transform("fileReader", new FileReaderSource)
      <span class="fragment semi-fade-out" data-fragment-index=1>.setParallelism(25)
      .assignTimestampsAndWatermarks(new WatermarkAssigner)

    messageStream
      .keyBy("id")
      .window(TumblingEventTimeWindows.of(Time.hours(1)))
      .sum("value")
      .setParallelism(50)
      .addSink(...)
  }
}</span>
</code></pre>


---

## Out with Lambda, In with Kappa

----

<img width="1200" class="plain" src="./images/old_event_arch.png" alt="Lambda Analytics" style="vertical-align: middle"/>

----

<img width="800" class="plain" src="./images/arc_demo.png" alt="Arc Example" style="vertical-align: middle"/>
Note: Arc is a video platform tailored for education and corporate that gives control and insights into what people are watching
Arc Insights shows data on how a video is being watched (or not). It is powered by a lambda architecture of spark batch and lambda/dynamo

----

### Lambda Architecture is no good

----

### Kappa Architecture solves everything right?

#### A step in the right direction, but a lot of ways to go about it

----

### Stream Retention

- Still a very unsolved problem
- Many message transport layers don't support infinite retention at all (Kinesis) or it can be expensive (Kafka)
- Even with more advanced solutions (Pulsar or Pravega tiered storage), scaling reads is non-trivial

----

### Reuse Stream Logic with two sources

- Another common pattern is to re-use logic in different jobs with different sources
- This requires external automation and/or external state to co-ordinate between the two different jobs

----

### Our Approach To Kappa

- We weren't ready to move to a new streaming transport and Kafka without trim seemed scary
- We also wanted to avoid external automation or state if possible
- Can we do this all inside Flink?

---

## The flexibility of Flink

----

<img width="1200" class="plain" src="./images/new_event_arch.png" alt="Kappa Analytics" style="vertical-align: middle"/>

----

### A better solution for archiving a stream

- By controlling our writes from Kinesis Streams to S3, we could read more granularly for back-fill
- Additionally, by writing offset metadata to AWS Glue, we can seamlessly transition to Kinesis

----

### Not the only challenge

- The `FlinkKinesisConsumer` is complex and not obviously re-usable as it is built on a `ParallelSourceFunction`
- In order to scale reads during back-fill, we need to read multiple files at once and then re-order the results <!-- .element: class="fragment" data-fragment-index="1" -->
- We still can't do bi-directional communication, which makes synchronization a challenge <!-- .element: class="fragment" data-fragment-index="2" -->
- We still have to worry about properly handling idle streams and watermarks to make downstream code work as expected <!-- .element: class="fragment" data-fragment-index="3" -->

----

### A more flexible "source"

<ul>
<li>With the `StreamOperator` API and our own `MessageableSource` abstraction, we could build an adapter for the <code>KinesisDataFetcher</code>, the <code>KinesisReader</code></li>
<li class="fragment" data-fragment-index=1>We also use the `MessageableSource` to build a "source" which recieves messages of files to read, the <code>S3FileReader</code></li>
<li class="fragment" data-fragment-index=2>These sources allowed us to back-fill data from S3, then transition to reading from Kinesis all with the same job graph, which we encapsulate in a single class, the `S3KinesisReader`</li>
</ul>

----

<img width="1000" class="plain" src="./images/s3_kinesis_graph.png" alt="S3Kinesis Reader Graph" style="vertical-align: middle"/>

----

<img width="1000" class="plain" src="./images/s3_kinesis_sequence.png" alt="S3Kinesis Reader Sequence" style="vertical-align: middle"/>

----

### Removing Unneeded Stuff

- Once we are done back-filling, we want to eventually scale down batch reading portions
- We can do this either by just scaling down the `S3FileReaders` or conditionally building a different graph

----

### Our results so far

- Much less business logic to maintain, first use case reduced from ~500 loc to ~100 loc
- Performance has been good in back-fill, but not strictly ordered
- Figuring out glue partitioning to avoid small partitions is a a challenge
- Still some bugs to iron out

----

### Upcoming Flink Changes

FLIP-27 - Refactor Source Interface

Aims to separate out sources into two components `SplitEnumerator` and `SplitReader`, which should
allow for more flexible sources

It also formalizes `Unbounded` and `Bounded` streams, which should make unified batch and streaming easier

----

### Should you do this?

<h3 class="fragment">¯\\_(ツ)_/¯ </h3>
----

### Why you might want to hold off

- Flink will get better support for unified batch/streaming
- This is pretty experimental, but working for us

---

## In Conclusion

----

- The `StreamOperator` API is how Flink achieves a lot of the features we know and love
- Understanding it can make it easier to reason about your Flink applications
- You can use it to do some really powerful things and build your own abstractions (but be careful)

----

### Talks you might want to see today:

- Towards Flink 2.0 - Nikko II & III at 2:00PM
- Moving From Lambda and Kappa to Kappa+ - Nikko II & III at 3:20PM

---

# Thanks!

## Questions?

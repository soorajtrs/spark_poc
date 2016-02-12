package com.spark.liberty.spark_poc;

import java.io.*;
import java.nio.channels.FileChannel;
import java.nio.ByteBuffer;
import java.net.URI;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.Scanner;
import java.util.Set;
import java.util.concurrent.*;

import scala.Tuple2;
import scala.Tuple3;
import scala.Tuple4;
import scala.collection.JavaConverters;

import com.google.common.collect.ImmutableMap;
import com.google.common.collect.Iterables;
import com.google.common.collect.Iterators;
import com.google.common.collect.Lists;
import com.google.common.collect.Maps;
import com.google.common.base.Throwables;
import com.google.common.base.Charsets;
import com.google.common.io.Files;

import org.apache.hadoop.io.IntWritable;
import org.apache.hadoop.io.Text;
import org.apache.hadoop.io.compress.DefaultCodec;
import org.apache.hadoop.mapred.SequenceFileInputFormat;
import org.apache.hadoop.mapred.SequenceFileOutputFormat;
import org.apache.hadoop.mapreduce.Job;
import org.junit.After;
import org.junit.Assert;
import org.junit.Before;
import org.junit.Test;
import org.apache.spark.api.java.JavaDoubleRDD;
import org.apache.spark.api.java.JavaFutureAction;
import org.apache.spark.api.java.JavaPairRDD;
import org.apache.spark.api.java.JavaRDD;
import org.apache.spark.api.java.JavaSparkContext;
import org.apache.spark.api.java.function.*;
import org.apache.spark.input.PortableDataStream;
import org.apache.spark.partial.BoundedDouble;
import org.apache.spark.partial.PartialResult;
import org.apache.spark.rdd.RDD;
import org.apache.spark.serializer.KryoSerializer;
import org.apache.spark.storage.StorageLevel;
import org.apache.spark.util.StatCounter;

// The test suite itself is Serializable so that anonymous Function implementations can be
// serialized, as an alternative to converting these anonymous classes to static inner classes;
// see http://stackoverflow.com/questions/758570/.
public class AppTest implements Serializable {
  private transient JavaSparkContext sc;
  private transient File tempDir;

  @Before
  public void setUp() {
    sc = new JavaSparkContext("local", "Junit_spark");
    tempDir = Files.createTempDir();
    tempDir.deleteOnExit();
  }

  @After
  public void tearDown() {
    sc.stop();
    sc = null;
  }

  @SuppressWarnings("unchecked")
  @Test
  public void sparkContextUnion() {
    // Union of non-specialized JavaRDDs
    List<String> strings = Arrays.asList("Hello", "World");
    JavaRDD<String> s1 = sc.parallelize(strings);
    JavaRDD<String> s2 = sc.parallelize(strings);
    // Varargs
    JavaRDD<String> sUnion = sc.union(s1, s2);
    Assert.assertEquals(4, sUnion.count());
    // List
    List<JavaRDD<String>> list = new ArrayList();
    list.add(s2);
    sUnion = sc.union(s1, list);
    Assert.assertEquals(4, sUnion.count());

    // Union of JavaDoubleRDDs
    List<Double> doubles = Arrays.asList(1.0, 2.0);
    JavaDoubleRDD d1 = sc.parallelizeDoubles(doubles);
    JavaDoubleRDD d2 = sc.parallelizeDoubles(doubles);
    JavaDoubleRDD dUnion = sc.union(d1, d2,d2);
    Assert.assertEquals(6, dUnion.count());

    // Union of JavaPairRDDs
    List<Tuple2<Integer, Integer>> pairs = new ArrayList();
    pairs.add(new Tuple2(1, 2));
    pairs.add(new Tuple2(3, 4));
    JavaPairRDD<Integer, Integer> p1 = sc.parallelizePairs(pairs);
    JavaPairRDD<Integer, Integer> p2 = sc.parallelizePairs(pairs);
    JavaPairRDD<Integer, Integer> pUnion = sc.union(p1, p2);
    Assert.assertEquals(4, pUnion.count());
  }

  @SuppressWarnings("unchecked")
  @Test
  public void intersection() {
    List<Integer> ints1 = Arrays.asList(1, 10, 2, 3, 4, 5);
    List<Integer> ints2 = Arrays.asList(1, 6, 2, 3, 7, 8);
    JavaRDD<Integer> s1 = sc.parallelize(ints1);
    JavaRDD<Integer> s2 = sc.parallelize(ints2);

    JavaRDD<Integer> intersections = s1.intersection(s2);
    Assert.assertEquals(3, intersections.count());

    JavaRDD<Integer> empty = sc.emptyRDD();
    JavaRDD<Integer> emptyIntersection = empty.intersection(s2);
    Assert.assertEquals(0, emptyIntersection.count());

    List<Double> doubles = Arrays.asList(1.0, 2.0);
    JavaDoubleRDD d1 = sc.parallelizeDoubles(doubles);
    JavaDoubleRDD d2 = sc.parallelizeDoubles(doubles);
    JavaDoubleRDD dIntersection = d1.intersection(d2);
    Assert.assertEquals(2, dIntersection.count());

    List<Tuple2<Integer, Integer>> pairs = new ArrayList();
    pairs.add(new Tuple2(1, 2));
    pairs.add(new Tuple2(3, 4));
    JavaPairRDD<Integer, Integer> p1 = sc.parallelizePairs(pairs);
    JavaPairRDD<Integer, Integer> p2 = sc.parallelizePairs(pairs);
    JavaPairRDD<Integer, Integer> pIntersection = p1.intersection(p2);
    Assert.assertEquals(2, pIntersection.count());
  }
  
  

  @SuppressWarnings("unchecked")
  @Test
  public void WC_test() {
//	  List<String> strings = Arrays.asList("Hello how hello", "World ");
	  
	  Scanner s = null;
	try {
		s = new Scanner(new File("input.txt"));
	} catch (FileNotFoundException e) {
		// TODO Auto-generated catch block
		e.printStackTrace();
	}
	  List<String> strings = new ArrayList<String>();
	  while (s.hasNext()){
		  strings.add(s.next());
	  }
	  s.close();
	  
	  
	  JavaRDD<String> input = sc.parallelize(strings);
	    // Split up into words.
	    JavaRDD<String> words = input.flatMap(
	      new FlatMapFunction<String, String>() {
	        public Iterable<String> call(String x) {
	          return Arrays.asList(x.split(" "));
	        }});
	    // Transform into word and count.
	    JavaPairRDD<String, Integer> counts = words.mapToPair(
	      new PairFunction<String, String, Integer>(){
	        public Tuple2<String, Integer> call(String x){
	          return new Tuple2(x, 1);
	        }}).reduceByKey(new Function2<Integer, Integer, Integer>(){
	            public Integer call(Integer x, Integer y){ return x + y;}});
	    // Save the word count back out to a text file, causing evaluation.
	  //  counts.saveAsTextFile(outputFile);
	    System.out.println("output ------->" + counts.toArray());
    Assert.assertEquals(7, counts.count());
    
  }
  
}

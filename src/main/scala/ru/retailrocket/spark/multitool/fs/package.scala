package ru.retailrocket.spark.multitool

import org.apache.hadoop.fs._
import org.apache.hadoop.conf.Configuration
import org.apache.hadoop.io.compress.CompressionCodec
import org.apache.spark.rdd.RDD
import java.io._
import java.nio.file.{FileAlreadyExistsException}


package object fs {
  val DefaultTempPath = "/tmp/spark"

  val DefaultCodec = classOf[org.apache.hadoop.io.compress.GzipCodec]

  def actionViaTemp(output: String, tempPath: Option[String]=None)(action: String => Unit)(store: (String, String) => Unit): Unit = {
    val tempRoot = tempPath getOrElse DefaultTempPath
    val temp = "%s_%d".format(tempRoot, System.currentTimeMillis)
    action(temp)
    store(temp, output)
  }

  def saveRddViaTemp[T](serializer: StringSerializer[T])(output: String, tempPath: Option[String]=None, codec: Option[Class[_ <: CompressionCodec]]=None)(store: (String, String) => Unit)(src: RDD[T]): Unit = {
    actionViaTemp(output, tempPath) { path => src.map(serializer.apply _).saveAsTextFile(path, codec getOrElse DefaultCodec) } (store)
  }

  def saveRddViaTempWithReplace[T](serializer: StringSerializer[T])(output: String, tempPath: Option[String]=None, codec: Option[Class[_ <: CompressionCodec]]=None)(src: RDD[T]): Unit = {
    saveRddViaTemp(serializer)(output, tempPath, codec)(replace _)(src)
  }

  def saveRddViaTempWithRename[T](serializer: StringSerializer[T])(output: String, tempPath: Option[String]=None, codec: Option[Class[_ <: CompressionCodec]]=None)(src: RDD[T]): Unit = {
    saveRddViaTemp(serializer)(output, tempPath, codec)(rename _)(src)
  }

  def saveStringViaTemp(output: String, tempPath: Option[String]=None, overwrite: Boolean = false )(store: (String, String) => Unit)(src: String): Unit = {
    actionViaTemp(output, tempPath) { path => storeHdfs(src, path, overwrite) } (store)
  }

  def saveStringViaTempWithReplace(output: String, tempPath: Option[String]=None, overwrite: Boolean = false )(src: String): Unit = {
    saveStringViaTemp(output, tempPath, overwrite)(replace _)(src)
  }

  def saveStringViaTempWithRename(output: String, tempPath: Option[String]=None, overwrite: Boolean = false )(src: String): Unit = {
    saveStringViaTemp(output, tempPath, overwrite)(rename _)(src)
  }

  def exists(dst: String): Boolean = {
    val fs = FileSystem.get(new Configuration())
    val dstPath = new Path(dst)
    fs.exists(dstPath)
  }

  def delete(dst: String, recursive: Boolean=true): Unit = {
    val fs = FileSystem.get(new Configuration())
    val dstPath = new Path(dst)
    if(fs.exists(dstPath)) fs.delete(dstPath, recursive)
  }

  def checkParentAndCreate(dst: Path): Unit = {
    val fs = FileSystem.get(new Configuration())
    val parent = dst.getParent
    if(!fs.exists(parent)) fs.mkdirs(parent)
  }

  def checkParentAndRename(src: Path, dst: Path) {
    val fs = FileSystem.get(new Configuration())
    checkParentAndCreate(dst)
    fs.rename(src, dst)
  }

  def rename(src: String, dst: String) = {
    val fs = FileSystem.get(new Configuration())
    val dstPath = new Path(dst)
    val srcPath = new Path(src)
    if(fs.exists(dstPath)) throw new FileAlreadyExistsException(s"path already exists - ${dst}")
    checkParentAndRename(srcPath, dstPath)
  }

  def replace(src: String, dst: String) = {
    val fs = FileSystem.get(new Configuration())
    val srcPath = new Path(src)
    val dstPath = new Path(dst)
    if(fs.exists(dstPath)) fs.delete(dstPath, true)
    checkParentAndRename(srcPath, dstPath)
  }

  def storeLocal(data: String, path: String) {
    val out = new FileOutputStream(path)
    val bytes = data.getBytes
    out.write(bytes, 0, bytes.size)
    out.close()
  }

  def storeHdfs(data: String, path: String, overwrite: Boolean = false) {
    val fs = FileSystem.get(new Configuration())
    val out = fs.create(new Path(path), overwrite)
    val bytes = data.getBytes
    out.write(bytes, 0, bytes.size)
    out.close()
  }

  def storeIterableToHdfs[T](serializer: StringSerializer[T])(path: String, overwrite: Boolean = false)(data: Iterable[T]) {
    val fs = FileSystem.get(new Configuration())
    val file = fs.create(new Path(path), overwrite)

    val writer = new BufferedWriter(new OutputStreamWriter(file))
    data.foreach { src =>
      writer.write(serializer(src))
      writer.newLine()
    }

    writer.close()
    file.close()
  }

  def createTempDirectoryLocal(prefix: String): String = {
    val temp = File.createTempFile(prefix, "")
    temp.delete()
    temp.mkdir()
    temp.getPath
  }
}

package io.prediction.commons.settings.mongodb

import io.prediction.commons.MongoUtils
import io.prediction.commons.settings.{ Engine, Engines }

import com.mongodb.casbah.Imports._

/** MongoDB implementation of Engines. */
class MongoEngines(db: MongoDB) extends Engines {
  private val engineColl = db("engines")
  private val seq = new MongoSequences(db)
  private val getFields = MongoDBObject("appid" -> 1, "name" -> 1, "infoid" -> 1, "itypes" -> 1, "params" -> 1)

  private def dbObjToEngine(dbObj: DBObject) = {
    /** Transparent upgrade. Remove in next minor version. */
    dbObj.getAs[DBObject]("settings") map { settings =>
      val e = Engine(
        id = dbObj.as[Int]("_id"),
        appid = dbObj.as[Int]("appid"),
        name = dbObj.as[String]("name"),
        infoid = dbObj.as[String]("infoid"),
        itypes = dbObj.getAs[MongoDBList]("itypes") map { MongoUtils.mongoDbListToListOfString(_) },
        params = MongoUtils.dbObjToMap(settings))
      update(e)
      e
    } getOrElse {
      Engine(
        id = dbObj.as[Int]("_id"),
        appid = dbObj.as[Int]("appid"),
        name = dbObj.as[String]("name"),
        infoid = dbObj.as[String]("infoid"),
        itypes = dbObj.getAs[MongoDBList]("itypes") map { MongoUtils.mongoDbListToListOfString(_) },
        params = MongoUtils.dbObjToMap(dbObj.as[DBObject]("params")))
    }
  }

  class MongoEngineIterator(it: MongoCursor) extends Iterator[Engine] {
    def next = dbObjToEngine(it.next)
    def hasNext = it.hasNext
  }

  def insert(engine: Engine) = {
    val id = seq.genNext("engineid")

    // required fields
    val obj = MongoDBObject(
      "_id" -> id,
      "appid" -> engine.appid,
      "name" -> engine.name,
      "infoid" -> engine.infoid,
      "params" -> engine.params
    )

    // optional fields
    val optObj = engine.itypes.map(x => MongoDBObject("itypes" -> x)).getOrElse(MongoUtils.emptyObj)

    engineColl.insert(obj ++ optObj)

    id
  }

  def get(id: Int) = engineColl.findOne(MongoDBObject("_id" -> id), getFields) map { dbObjToEngine(_) }

  def getAll() = new MongoEngineIterator(engineColl.find())

  def getByAppid(appid: Int) = new MongoEngineIterator(engineColl.find(MongoDBObject("appid" -> appid)).sort(MongoDBObject("name" -> 1)))

  def getByAppidAndName(appid: Int, name: String) = engineColl.findOne(MongoDBObject("appid" -> appid, "name" -> name)) map { dbObjToEngine(_) }

  def getByIdAndAppid(id: Int, appid: Int): Option[Engine] = engineColl.findOne(MongoDBObject("_id" -> id, "appid" -> appid)) map { dbObjToEngine(_) }

  def update(engine: Engine, upsert: Boolean = false) = {
    val idObj = MongoDBObject("_id" -> engine.id)
    val nameObj = MongoDBObject("name" -> engine.name)
    val appidObj = MongoDBObject("appid" -> engine.appid)
    val infoidObj = MongoDBObject("infoid" -> engine.infoid)
    val itypesObj = engine.itypes.map(x => MongoDBObject("itypes" -> x)).getOrElse(MongoUtils.emptyObj)
    val paramsObj = MongoDBObject("params" -> engine.params)

    engineColl.update(
      idObj,
      idObj ++ appidObj ++ nameObj ++ infoidObj ++ itypesObj ++ paramsObj,
      upsert
    )
  }

  def deleteByIdAndAppid(id: Int, appid: Int) = engineColl.remove(MongoDBObject("_id" -> id, "appid" -> appid))

  def existsByAppidAndName(appid: Int, name: String) = engineColl.findOne(MongoDBObject("name" -> name, "appid" -> appid)) map { _ => true } getOrElse false
}

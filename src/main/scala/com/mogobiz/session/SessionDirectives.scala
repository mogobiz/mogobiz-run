package com.mogobiz.session

/**
 * Created by hayssams on 04/03/14.
 */

import com.mogobiz.config.Settings
import shapeless.{::, _}
import spray.http.HttpCookie
import spray.http.HttpHeaders.Cookie
import spray.routing._
import scala.language.implicitConversions

import scala.collection.mutable.Map

case object MissingSessionCookieRejection extends Rejection

trait SessionDirectives {
  this: Backend =>

  import spray.routing.directives.BasicDirectives._
  import spray.routing.directives.CookieDirectives._
  import spray.routing.directives.HeaderDirectives._

  def session: Directive[Session :: HNil] = headerValue {
    case Cookie(cookies) => cookies.find(_.name == Settings.SessionCookieName) map {
      sessionFromCookie
    }
    case _ => None
  } | provide(Session())


  def optionalSession: Directive[Option[Session] :: HNil] =
    session.hmap(_.map(shapeless.option)) | provide(None)

  def setSession(session: Session): Directive0 = {
    setCookie(Session(session.data))
  }


  def deleteSession(domain: String = "", path: String = ""): Directive0 =
    deleteCookie(Settings.SessionCookieName, domain, path)

  implicit def sessionFromCookie(cookie: HttpCookie): Session =
    Session(load(cookie.content).getOrElse(Map.empty[String, Any]), cookie.expires, cookie.maxAge, cookie.domain, cookie.path, cookie.secure, cookie.httpOnly, cookie.extension)

  implicit def sessionToCookie(session: Session): HttpCookie = {
    val res = HttpCookie(Settings.SessionCookieName, store(session.data), session.expires, session.maxAge, session.domain, session.path, session.secure, session.httpOnly, session.extension)
    println(res)
    res

  }
}

trait Backend {
  def store(data: Session.Data): String

  def load(data: String): Option[Session.Data]
}

trait CookieBackend extends Backend {
  def store(data: Session.Data): String = {
    val encoded = java.net.URLEncoder.encode(data.filterNot(_._1.contains(":")).map(d => d._1 + ":" + d._2).mkString("\u0000"), "UTF-8")
    Crypto.sign(encoded) + "-" + encoded
  }

  def load(data: String): Option[Session.Data] = {
    def urldecode(data: String) = Map[String, Any](java.net.URLDecoder.decode(data, "UTF-8").split("\u0000").map(_.split(":")).map(p => p(0) -> p.drop(1).mkString(":")): _*)
    // Do not change this unless you understand the security issues behind timing attacks.
    // This method intentionally runs in constant time if the two strings have the same length.
    // If it didn't, it would be vulnerable to a timing attack.
    def safeEquals(a: String, b: String) = {
      if (a.length != b.length) false
      else {
        var equal = 0
        for (i <- Array.range(0, a.length)) {
          equal |= a(i) ^ b(i)
        }
        equal == 0
      }
    }

    try {
      val splitted = data.split("-")
      val message = splitted.tail.mkString("-")
      if (safeEquals(splitted(0), Crypto.sign(message)))
        Some(urldecode(message))
      else
        None
    } catch {
      // fail gracefully is the session cookie is corrupted
      case scala.util.control.NonFatal(_) => None
    }
  }

}

/*
trait FileBackend extends Backend {
  private def filename(bucket: String, key: String) = s"$bucket-$key"

  private val converter = new BinaryConverter[Session.Data] {}

  def store(data: Session.Data): String = {
    val uuid = data(Settings.SessionCookieName).asInstanceOf[String]
    val raw = converter.fromDomain(data)
    val sessionFile = new File(Settings.SessionFolder, filename("session", uuid))
    val out = new FileOutputStream(sessionFile)
    val buffer = new BufferedOutputStream(out);
    val output = new ObjectOutputStream(buffer);
    try {
      output.writeObject(raw);
    } finally {
      output.close();
    }
    uuid
  }

  def load(data: String): Option[Session.Data] = {
    val sessionFile = new FileInputStream(new File(Settings.SessionFolder, filename("session", data)))
    try {
      val buffer = new BufferedInputStream(sessionFile)
      val input = new ObjectInputStream(buffer)
      val raw = input.readObject().asInstanceOf[Array[Byte]]
      Some(converter.toDomain(raw))
    } catch {
      case e: Throwable =>
        e.printStackTrace()
        None
    } finally {
      sessionFile.close();
    }
  }

}*/

object SessionCookieDirectives extends SessionDirectives with CookieBackend

//object SessionFileDirectives extends SessionDirectives with FileBackend



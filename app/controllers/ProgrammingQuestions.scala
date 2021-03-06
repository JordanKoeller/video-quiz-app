package controllers

import play.api._
import play.api.mvc._
import play.api.db.slick.DatabaseConfigProvider
import play.api.i18n.MessagesApi
import javax.inject.Inject
import slick.jdbc.JdbcProfile
import slick.jdbc.MySQLProfile.api._
import models._
import Tables._
import scala.concurrent.ExecutionContext
import scala.concurrent.Future
import java.sql.Timestamp
import java.time.LocalDateTime
import play.api.db.slick.HasDatabaseConfigProvider

class ProgrammingQuestions @Inject() (
    protected val dbConfigProvider: DatabaseConfigProvider, 
    mcc: MessagesControllerComponents)(implicit ec: ExecutionContext) extends MessagesAbstractController(mcc) with HasDatabaseConfigProvider[JdbcProfile] {

  import ControlHelpers._
  implicit val actionBuilder = Action
  
  def submitQuiz = AuthenticatedAction { implicit request =>
      val userid = request.session("userid").toInt
      request.body.asFormUrlEncoded match {
        case Some(params) =>
          val quizid = params("quizid")(0).toInt
          // Get quiz specs from database and check correctness
          for (key <- params.keys; if key.startsWith("mc-")) {
            val mcid = key.drop(3).toInt
            val pspec = ProblemSpec(ProblemSpec.MultipleChoiceType, mcid, db)
            val correct = pspec.map(_.checkResponse(params(key)(0)))
            val selection = try { params(key)(0).toInt } catch { case e: NumberFormatException => -1 }
            correct.map(c => db.run(McAnswers += McAnswersRow(Some(userid), Some(quizid), Some(mcid), selection, c, Timestamp.valueOf(LocalDateTime.now()))))
          }
          for (key <- params.keys; if key.startsWith("code-")) {
            val Array(codeid, qtype) = key.drop(5).split("-")
            val pspec = ProblemSpec(qtype.toInt, codeid.toInt, db)
            val correct = pspec.map(_.checkResponse(params(key)(0)))
            correct.map(c => db.run(CodeAnswers += CodeAnswersRow(Some(userid), Some(quizid), codeid.toInt, qtype.toInt, params(key)(0), c, Timestamp.valueOf(LocalDateTime.now()))))
          }
        case None =>
      }
      Future(Redirect(routes.Application.quizList()).flashing("refresh-delay" -> "5"))
  }
  
  def compileAnswer(quizType: Int, codeid: Int) = AuthenticatedAction { implicit request =>
    ProblemSpec(quizType, codeid, db).map { pspec =>
      Ok("").as("application/javascript")
    }
  }
  
}
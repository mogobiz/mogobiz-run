import scalikejdbc._, SQLInterpolation._
//import scalikejdbc.config._

Class.forName("org.postgresql.Driver")
val url = "jdbc:postgresql://localhost/iper2010"
val user = "iper2010"
val password = "iper2010"
ConnectionPool.singleton(url, user, password)



//using(ConnectionPool.borrow()) { conn => }
//using(DB(ConnectionPool.borrow())) { db => }
val res = DB readOnly { implicit session =>
  //
  sql"select * from tax_rate".map(rs => (rs.long("id"),rs.long("company_fk"),rs.string("name"))).list().apply()
}






























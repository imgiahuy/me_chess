package model

case class Player (name : String, points : Int, color : Color) {
  def myInfos() : (String, Int, String
    ) = (this.name, this.points, this.color.toString)
}

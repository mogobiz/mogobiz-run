package com.mogobiz.run.utils

/**
 * Created by Christophe on 26/08/2014.
 */
object MailTemplateUtils {

  
  def ticket(eventName :String,startDate:String,stopDate:String,location:String, price: String, eventType:String, qrcodeUrl:String) : String =
    s"""
       |<table border="0" bordercolor="" style="background-color:#FFFFFF" width="70%" cellpadding="3" cellspacing="0">
	<tr>
		<td>Event</td>
		<td>$eventName</td>
	</tr>
	<tr>
		<td>When</td>
		<td>From $startDate <br>
			To $stopDate </td>
	</tr>
	<tr>
		<td>Where</td>
		<td>$location</td>
	</tr>
	<tr>
		<td>Price</td>
		<td>$price</td>
	</tr>
	<tr>
		<td>Type</td>
		<td>$eventType</td>
	</tr>
	<tr>
		<td>MyID</td>
		<td><img src='$qrcodeUrl'></td>
	</tr>
</table>

     """.stripMargin
}

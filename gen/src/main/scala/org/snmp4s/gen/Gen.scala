/*
 * Copyright 2013 org.snmp4s
 * Distributed under the terms of the GNU General Public License v3
 */

package org.snmp4s.gen

import net.percederberg.mibble._
import net.percederberg.mibble.snmp._
import net.percederberg.mibble.`type`._
import java.io.File
import scala.collection.JavaConversions._

protected object Util {
  def name2oid(mib:Mib) = {
    val syms = mib.getAllSymbols()
    (for {
        sym <- syms
        if (sym.isInstanceOf[MibValueSymbol])
      } yield {
        val s = sym.asInstanceOf[MibValueSymbol]
        s.getName -> s
      }).toMap
  }
}

class Gen {
  def load(name:String):Mib = {
    val loader = new MibLoader
    loader load name
  }
  
  def load(file:File):Seq[Mib] = {
    val loader = new MibLoader
    loader.addDir(file)
    
    (for {
      m <- file.listFiles()
      if m isFile
    } yield {
      try {
        Some(loader load m)
      } catch {
        case e: MibLoaderException =>
          e.getLog.printTo(System.err)
          None
      }
    }).flatten.toSeq
  }
  
  def code(oid:MibValueSymbol):String = {
    val name = oid.getName
    val objName = name.substring(0, 1).toUpperCase() + name.substring(1)
    if(oid.getType.isInstanceOf[SnmpObjectType]) {
      val snmp = oid.getType.asInstanceOf[SnmpObjectType]
      val access = accessMap.get(snmp.getAccess()).get
      val octets = oid.getValue.toString.replace(".", ",")
      val (scalaType, enumArg, typeCode) = syntax(objName, snmp.getSyntax)
      
      val code = typeCode + s"""case object $objName extends AccessibleObject[$access, $scalaType](Seq($octets), "$name"$enumArg)"""
      if(oid.isScalar) code + s" with Scalar[$access, $scalaType]"
      else code
    } else {
      ""
    }
  }
  
  private def syntax(objName:String, syntax:MibType):(String,String,String) = {
    if(syntax.isInstanceOf[IntegerType]) {
      val intType = syntax.asInstanceOf[IntegerType]
      if(intType.hasSymbols) syntaxEnum(objName, intType)
      else syntaxGeneral(syntax)
    }
    else syntaxGeneral(syntax)
  }
  
  private def syntaxEnum(objName:String, syntax:IntegerType):(String,String,String) = {
    val scalaType = objName+"_enum.Value"
    val enumArg = ", Some("+objName+"_enum)"
    
    val typeHead = "object "+objName+"_enum extends EnumInteger {\n"+
    "  type "+objName+" = Value\n"
    val entries = for {
      s <- syntax.getAllSymbols.toList
    } yield {
      val v = s.getValue
      val nl = s.getName
      val nu = nl.substring(0, 1).toUpperCase() + nl.substring(1)
      s"""  val $nu = Value($v, "$nl")\n"""
    }
    val typeTail = "}\n"
    
    val typeCode = typeHead + entries.mkString + typeTail
      
    (scalaType, enumArg, typeCode)
  }
  
  private def syntaxGeneral(syntax:MibType):(String,String,String) = (syntaxMap.get(syntax.getName).get, "", "")
  
  private val accessMap = Map(
    SnmpAccess.READ_WRITE -> "ReadWrite",
    SnmpAccess.NOT_ACCESSIBLE -> "NotAccessible",
    SnmpAccess.ACCESSIBLE_FOR_NOTIFY -> "AccessibleForNotify",
    SnmpAccess.READ_CREATE -> "ReadCreate",
    SnmpAccess.READ_ONLY -> "ReadOnly",
    SnmpAccess.WRITE_ONLY -> "WriteOnly"
  )
  
  private val syntaxMap = Map(
    "INTEGER" -> "Int",
    "OCTET STRING" -> "String"
  )
}
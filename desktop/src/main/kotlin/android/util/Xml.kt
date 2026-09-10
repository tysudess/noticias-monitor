package android.util

import org.kxml2.io.KXmlParser
import org.xmlpull.v1.XmlPullParser

object Xml {
    fun newPullParser(): XmlPullParser = KXmlParser()
}

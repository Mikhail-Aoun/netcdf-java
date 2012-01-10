/*
 * Copyright (c) 1998 - 2012. University Corporation for Atmospheric Research/Unidata
 * Portions of this software were developed by the Unidata Program at the
 * University Corporation for Atmospheric Research.
 *
 * Access and use of this software shall impose the following obligations
 * and understandings on the user. The user is granted the right, without
 * any fee or cost, to use, copy, modify, alter, enhance and distribute
 * this software, and any derivative works thereof, and its supporting
 * documentation for any purpose whatsoever, provided that this entire
 * notice appears in all copies of the software, derivative works and
 * supporting documentation.  Further, UCAR requests that the user credit
 * UCAR/Unidata in any publications that result from the use of this
 * software or in any product that includes this software. The names UCAR
 * and/or Unidata, however, may not be used in any advertising or publicity
 * to endorse or promote any products or commercial entity unless specific
 * written permission is obtained from UCAR/Unidata. The user also
 * understands that UCAR/Unidata is not obligated to provide the user with
 * any support, consulting, training or assistance of any kind with regard
 * to the use, operation and performance of this software nor to provide
 * the user with any updates, revisions, new versions or "bug fixes."
 *
 * THIS SOFTWARE IS PROVIDED BY UCAR/UNIDATA "AS IS" AND ANY EXPRESS OR
 * IMPLIED WARRANTIES, INCLUDING, BUT NOT LIMITED TO, THE IMPLIED
 * WARRANTIES OF MERCHANTABILITY AND FITNESS FOR A PARTICULAR PURPOSE ARE
 * DISCLAIMED. IN NO EVENT SHALL UCAR/UNIDATA BE LIABLE FOR ANY SPECIAL,
 * INDIRECT OR CONSEQUENTIAL DAMAGES OR ANY DAMAGES WHATSOEVER RESULTING
 * FROM LOSS OF USE, DATA OR PROFITS, WHETHER IN AN ACTION OF CONTRACT,
 * NEGLIGENCE OR OTHER TORTIOUS ACTION, ARISING OUT OF OR IN CONNECTION
 * WITH THE ACCESS, USE OR PERFORMANCE OF THIS SOFTWARE.
 */

package ucar.nc2.grib.grib2.table;

import org.jdom.Element;
import org.jdom.JDOMException;
import org.jdom.input.SAXBuilder;
import ucar.nc2.grib.GribResourceReader;
import ucar.nc2.grib.GribTables;
import ucar.nc2.iosp.grid.GridParameter;
import ucar.nc2.units.SimpleUnit;
import ucar.unidata.util.StringUtil2;

import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.util.*;

/**
 * Read Ncep parameter table in screenscraped xml format.
 *
 * @author caron
 * @since 1/9/12
 */
public class NcepTable {
  private static boolean debug = false;

  String title;
  String source;
  String tableName;
  int discipline, category;
  Map<Integer, Grib2Parameter> paramMap;

  NcepTable(String path) {
    readParameterTableXml(path);
  }

  public List<Grib2Parameter> getParameters() {
    List<Grib2Parameter> result = new ArrayList<Grib2Parameter>(paramMap.values());
    Collections.sort(result);
    return result;
  }

  public Grib2Parameter getParameter(int code) {
    return paramMap.get(code);
  }

  private boolean readParameterTableXml(String path) {
    InputStream is = null;
    try {
      is = GribResourceReader.getInputStream(path);
      if (is == null) return false;

      SAXBuilder builder = new SAXBuilder();
      org.jdom.Document doc = builder.build(is);
      Element root = doc.getRootElement();
      paramMap = parseXml(root);  // all at once - thread safe
      return true;

    } catch (IOException ioe) {
      ioe.printStackTrace();
      return false;

    } catch (JDOMException e) {
      e.printStackTrace();
      return false;

    } finally {
      if (is != null) try {
        is.close();
      } catch (IOException e) {
      }
    }
  }

  /*
  <parameterMap>
  <table>Table4.2.0.0</table>
  <title>Temperature</title>
  <source>http://www.nco.ncep.noaa.gov/pmb/docs/grib2/grib2_table4-2-0-0.shtml</source>
  <parameter code="0">
    <shortName>TMP</shortName>
    <description>Temperature</description>
    <units>K</units>
  </parameter>
   */
  public HashMap<Integer, Grib2Parameter> parseXml(Element root) {
    tableName = root.getChildText("table");
    title = root.getChildText("title");
    source = root.getChildText("source");

    // Table4.2.0.0
    int pos = tableName.indexOf(match);
    String dc = tableName.substring(pos+match.length());
    String[] dcs = dc.split("\\.");
    discipline =  Integer.parseInt(dcs[0]);
    category =  Integer.parseInt(dcs[1]);

    HashMap<Integer, Grib2Parameter> result = new HashMap<Integer, Grib2Parameter>();
    List<Element> params = root.getChildren("parameter");
    for (Element elem : params) {
      int code = Integer.parseInt(elem.getAttributeValue("code"));
      String abbrev = elem.getChildText("shortName");
      String name = elem.getChildText("description");
      String units = elem.getChildText("units");
      if (units == null) units = "";

      //   public Grib2Parameter(int discipline, int category, int number, String name, String unit, String abbrev) {
      Grib2Parameter parameter = new Grib2Parameter(discipline, category, code, name, units, abbrev);
      result.put(parameter.getNumber(), parameter);
      if (debug) System.out.printf(" %s%n", parameter);
    }
    return result;
  }

  @Override
  public String toString() {
    final StringBuilder sb = new StringBuilder();
    sb.append("NcepTable");
    sb.append("{title='").append(title).append('\'');
    sb.append(", source='").append(source).append('\'');
    sb.append(", tableName='").append(tableName).append('\'');
    sb.append('}');
    return sb.toString();
  }

  private static final String match = "Table4.2.";

  //////////////////////////////////////////////////////////////////////////

  private static void compareTables(NcepTable test, Grib2Tables current) {
    Formatter f = new Formatter();
    //f.format("Table 1 = %s%n", test.tableName);
    //f.format("Table 2 = %s%n", "currentNcep");

    int extra = 0;
    int udunits = 0;
    int conflict = 0;
    // f.format("Table 1 : %n");
    for (Grib2Parameter p1 : test.getParameters()) {
      Grib2Tables.Parameter  p2 = current.getParameter(p1.getDiscipline(), p1.getCategory(), p1.getNumber());
      if (p2 == null) {
        extra++;
        if (p1.getNumber() < 192) f.format("  Missing %s%n", p1);

      } else {
        String p1n = StringUtil2.substitute(GridParameter.cleanupDescription(p1.getName()), "-", " ");
        String p2n = StringUtil2.substitute(GridParameter.cleanupDescription(p2.getName()), "-", " ");

        if (!p1n.equalsIgnoreCase(p2n) ||
           (p1.getNumber() >= 192 && !p1.getAbbrev().equals(p2.getAbbrev()))) {
          f.format("  p1=%10s %40s %15s  %15s%n", p1.getId(), p1.getName(), p1.getUnit(), p1.getAbbrev());
          f.format("  p2=%10s %40s %15s  %15s%n%n", p2.getId(), p2.getName(), p2.getUnit(), p2.getAbbrev());
          conflict++;
        }

        if (!p1.getUnit().equalsIgnoreCase(p2.getUnit())) {
          String cu1 = GridParameter.cleanupUnits(p1.getUnit());
          String cu2 = GridParameter.cleanupUnits(p2.getUnit());

          // eliminate common non-udunits
          boolean isUnitless1 = isUnitless(cu1);
          boolean isUnitless2 = isUnitless(cu2);

          if (isUnitless1 != isUnitless2) {
            f.format("  ud=%10s %s != %s for %s (%s)%n%n", p1.getId(), cu1, cu2, p1.getId(), p1.getName());
            udunits++;

          } else if (!isUnitless1) {

            try {
              SimpleUnit su1 = SimpleUnit.factoryWithExceptions(cu1);
              if (!su1.isCompatible(cu2)) {
                f.format("  ud=%10s %s (%s) != %s for %s (%s)%n%n", p1.getId(), cu1, su1, cu2, p1.getId(), p1.getName());
                udunits++;
              }
            } catch (Exception e) {
              f.format("  udunits cant parse=%10s %15s %15s%n", p1.getId(), cu1, cu2);
            }
          }

        }
      }

    }
    f.format("Conflicts=%d extra=%d udunits=%d%n%n", conflict, extra, udunits);

    /* extra = 0;
    f.format("Table 2 : %n");
    for (Object t : current.getParameters()) {
      Grib2Tables.Parameter p2 = (Grib2Tables.Parameter) t;
      Grib2Parameter  p1 = test.getParameter(p2.getNumber());
      if (p1 == null) {
        extra++;
        f.format(" Missing %s in table 1%n", p2);
      }
    }
    f.format("%nextra=%d%n%n", extra); */
    System.out.printf("%s%n", f);
  }

  static boolean isUnitless(String unit) {
    if (unit == null) return true;
    String munge = unit.toLowerCase().trim();
    munge = StringUtil2.remove(munge, '(');
    return munge.length()  == 0 ||
            munge.startsWith("numeric") || munge.startsWith("non-dim") || munge.startsWith("see") ||
            munge.startsWith("proportion") || munge.startsWith("code") || munge.startsWith("0=") ||
            munge.equals("1") ;
  }


  public static void main(String[] args) {
    boolean test1 = isUnitless("See.Table.4.201");
    boolean test2 = isUnitless("(Code.table.4.201)");

    Grib2Tables current = Grib2Tables.factory(7, -1, -1, -1);

    File dir = new File("C:\\dev\\github\\thredds\\grib\\src\\main\\resources\\resources\\grib2\\ncep");
    for (File f : dir.listFiles()) {
      if (f.getName().startsWith(match)) {
        NcepTable nt = new NcepTable(f.getPath());
        System.out.printf("%s%n", nt);
        compareTables(nt, current);
      }
    }
  }
}

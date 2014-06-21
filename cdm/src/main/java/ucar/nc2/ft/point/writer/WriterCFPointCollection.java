/*
 * Copyright (c) 1998 - 2014. University Corporation for Atmospheric Research/Unidata
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

package ucar.nc2.ft.point.writer;

import ucar.nc2.constants.CDM;
import ucar.nc2.constants.CF;
import ucar.nc2.dataset.conv.CF1Convention;
import ucar.nc2.time.CalendarDate;
import ucar.nc2.units.DateUnit;
import ucar.nc2.*;
import ucar.nc2.ft.*;
import ucar.unidata.geoloc.EarthLocation;
import ucar.ma2.*;

import java.util.*;
import java.io.IOException;

/**
 * Write a CF 1.6 "Discrete Sample" point file.
 *
 * <pre>
 *   writeHeader()
 *   iterate { writeRecord() }
 *   finish()
 * </pre>
 *
 * @see "http://cf-pcmdi.llnl.gov/documents/cf-conventions/1.6/cf-conventions.html#idp8294224"
 * @author caron
 * @since Nov 23, 2010
 */
public class WriterCFPointCollection extends CFPointWriter {
  private Variable time, lat, lon, alt;

  public WriterCFPointCollection(String fileOut, String title) throws IOException {
    this(null, fileOut, Arrays.asList(new Attribute(CDM.TITLE, title)));
  }

  public WriterCFPointCollection(NetcdfFileWriter.Version version, String fileOut, List<Attribute> atts) throws IOException {
    super(fileOut, atts, version);
    writer.addGroupAttribute(null, new Attribute(CF.FEATURE_TYPE, CF.FeatureType.point.name()));
  }

  public void writeHeader(List<VariableSimpleIF> vars, DateUnit timeUnit, String altUnits) throws IOException {
    this.altUnits = altUnits;
    writer.addUnlimitedDimension(recordDimName);

    List<VariableSimpleIF> coords = new ArrayList<>();
    coords.add(VariableSimpleImpl.make(timeName, "time of measurement", timeUnit.getUnitsString(), DataType.DOUBLE));
    coords.add(VariableSimpleImpl.make(latName,  "station latitude", CDM.LAT_UNITS, DataType.DOUBLE));
    coords.add(VariableSimpleImpl.make(lonName,  "station longitude", CDM.LON_UNITS, DataType.DOUBLE));
    if (altUnits != null) coords.add(
          VariableSimpleImpl.make(altName, "altitude", altUnits, DataType.DOUBLE)
          .add(new Attribute(CF.POSITIVE, CF1Convention.getZisPositive(altName, altUnits))));

    if (writer.getVersion().isExtendedModel()) {
      record = (Structure) writer.addVariable(null, recordName, DataType.STRUCTURE, recordDimName);
      addCoordinatesExtended(coords);
      addVariablesExtended(vars);
      record.calcElementSize();
      writer.create();

    } else {
      addCoordinatesClassic(coords);
      addDataVariablesClassic(vars);
      writer.create();
      record = writer.addRecordStructure(); // netcdf3

      time = writer.findVariable(timeName);
      lat = writer.findVariable(latName);
      lon = writer.findVariable(lonName);
      alt = writer.findVariable(altName);
    }
  }

  protected void addCoordinatesClassic(List<VariableSimpleIF> coords) throws IOException {

   // added as variables with the unlimited (record) dimension
    for (VariableSimpleIF vs : coords) {
      Variable member = writer.addVariable(null, vs.getShortName(), vs.getDataType(), recordDimName);
      for (Attribute att : vs.getAttributes())
        member.addAttribute(att);
    }

  }

  // added as members of the record structure
  protected void addCoordinatesExtended(List<VariableSimpleIF> coords) throws IOException {

    for (VariableSimpleIF vs : coords) {
      Variable member = writer.addStructureMember(record, vs.getShortName(), vs.getDataType(), null);
      for (Attribute att : vs.getAttributes())
        member.addAttribute(att);
    }

  }

  /////////////////////////////////////////////////////////
  // writing data

  private int recno = 0;
  private ArrayDouble.D1 timeArray = new ArrayDouble.D1(1);
  private ArrayDouble.D1 latArray = new ArrayDouble.D1(1);
  private ArrayDouble.D1 lonArray = new ArrayDouble.D1(1);
  private ArrayDouble.D1 altArray = new ArrayDouble.D1(1);

  public void writeRecord(PointFeature sobs, StructureData sdata) throws IOException {
    writeRecord(sobs.getObservationTime(), sobs.getObservationTimeAsCalendarDate(), sobs.getLocation(), sdata);
  }

  public void writeRecord(double timeCoordValue, CalendarDate obsDate, EarthLocation loc, StructureData sdata) throws IOException {
    trackBB(loc, obsDate);

    StructureDataScalar coords = new StructureDataScalar("Coords");
    coords.addMember(timeName, "time of measurement", null, timeCoordValue);
    coords.addMember(latName,  "station latitude", CDM.LAT_UNITS, loc.getLatitude());
    coords.addMember(lonName,  "station longitude", CDM.LON_UNITS, loc.getLongitude());
    if (altUnits != null) coords.addMember(altName, "altitude", altUnits, loc.getAltitude());

    StructureDataComposite sdall = new StructureDataComposite();
    sdall.add(coords); // coords first so it takes precedence
    sdall.add(sdata);

    // write the recno record
    int[] origin = new int[1];
    origin[0] = recno;
    try {
      super.writeStructureData(origin, sdall);

      if (isExtendedModel) {
        // throw new RuntimeException("extended model not working yet");

      } else {
        timeArray.set(0, timeCoordValue);
        latArray.set(0, loc.getLatitude());
        lonArray.set(0, loc.getLongitude());
        if (altUnits != null)
          altArray.set(0, loc.getAltitude());

        writer.write(time, origin, timeArray);
        writer.write(lat, origin, latArray);
        writer.write(lon, origin, lonArray);
        if (altUnits != null)
          writer.write(alt, origin, altArray);
      }

    } catch (InvalidRangeException e) {
      e.printStackTrace();
      throw new IllegalStateException(e);
    }

    recno++;
  }

}
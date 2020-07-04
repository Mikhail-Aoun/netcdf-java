/*
 * Copyright (c) 1998-2020 John Caron and University Corporation for Atmospheric Research/Unidata
 * See LICENSE for license information.
 */
package ucar.nc2.write;

import org.junit.Rule;
import org.junit.Test;
import org.junit.experimental.categories.Category;
import org.junit.rules.TemporaryFolder;
import org.junit.runner.RunWith;
import org.junit.runners.Parameterized;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import ucar.nc2.NetcdfFile;
import ucar.nc2.util.CompareNetcdf2;
import ucar.unidata.util.test.category.NeedsCdmUnitTest;
import ucar.unidata.util.test.TestDir;
import java.io.File;
import java.io.IOException;
import java.lang.invoke.MethodHandles;
import java.util.ArrayList;
import java.util.List;

/** Test NetcdfCopier, write copy, then read back and comparing to original. */
@Category(NeedsCdmUnitTest.class)
@RunWith(Parameterized.class)
public class TestNetcdfCopier {
  private static final Logger logger = LoggerFactory.getLogger(MethodHandles.lookup().lookupClass());

  @Rule
  public final TemporaryFolder tempFolder = new TemporaryFolder();

  @Parameterized.Parameters(name = "{0}")
  public static List<Object[]> getTestParameters() {
    List<Object[]> result = new ArrayList<>();

    // unlimited dimesnion = 0
    result.add(new Object[] {TestDir.cdmUnitTestDir + "formats/netcdf3/longOffset.nc", true});
    result.add(new Object[] {TestDir.cdmUnitTestDir + "formats/gempak/grid/dgex_le.gem", true});
    result.add(new Object[] {TestDir.cdmUnitTestDir + "formats/grib1/radar_national.grib", true});
    result.add(new Object[] {TestDir.cdmUnitTestDir + "formats/grib2/200508041200.ngrid_gfs", true});
    result.add(new Object[] {"file:" + TestDir.cdmLocalFromTestDataDir + "point/stationData2Levels.ncml", false});

    // result.add(new Object[]{"formats/hdf4/17766010.hdf"});
    // result.add(new Object[]{"formats/dmsp/F14200307192230.n.OIS", true});

    return result;
  }

  private final String filename;
  private final boolean same;

  public TestNetcdfCopier(String filename, boolean same) {
    this.filename = filename;
    this.same = same;
  }

  @Test
  public void doOne() throws IOException {
    File fin = new File(filename);
    File fout = tempFolder.newFile();
    System.out.printf("Write %s %n   to %s (%s %s)%n", fin.getAbsolutePath(), fout.getAbsolutePath(), fout.exists(),
        fout.getParentFile().exists());

    try (NetcdfFile ncfileIn = ucar.nc2.dataset.NetcdfDatasets.openFile(fin.getPath(), null)) {
      NetcdfFormatWriter.Builder builder = NetcdfFormatWriter.createNewNetcdf3(fout.getPath());
      NetcdfCopier copier = NetcdfCopier.create(ncfileIn, builder);
      try (NetcdfFile ncfileOut = copier.write(null)) {
        assert new CompareNetcdf2().compare(ncfileIn, ncfileOut) == same;
      }
    }
    System.out.printf("%n");
  }

}

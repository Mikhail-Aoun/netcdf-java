/*
 * Copyright 2006 Jerry Huxtable
 * 
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 * 
 * http://www.apache.org/licenses/LICENSE-2.0
 * 
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

/*
 * This file was semi-automatically converted from the public-domain USGS PROJ source.
 */
package ucar.unidata.geoloc.projection.proj4;

import java.util.Formatter;
import javax.annotation.concurrent.Immutable;
import ucar.nc2.constants.CDM;
import ucar.nc2.constants.CF;
import ucar.unidata.geoloc.Earth;
import ucar.unidata.geoloc.EarthEllipsoid;
import ucar.unidata.geoloc.LatLonPoint;
import ucar.unidata.geoloc.LatLonPoints;
import ucar.unidata.geoloc.Projection;
import ucar.unidata.geoloc.projection.AbstractProjection;
import ucar.unidata.geoloc.ProjectionPoint;

/**
 * Transverse Mercator Projection algorithm is taken from the USGS PROJ package.
 *
 * @author Heiko.Klein@met.no
 */
@Immutable
public class TransverseMercatorProjection extends AbstractProjection {

  public static int getRowFromNearestParallel(double latitude) {
    int degrees = (int) MapMath.radToDeg(MapMath.normalizeLatitude(latitude));
    if (degrees < -80 || degrees > 84)
      return 0;
    if (degrees > 80)
      return 24;
    return (degrees + 80) / 8 + 3;
  }

  public static int getZoneFromNearestMeridian(double longitude) {
    int zone = (int) Math.floor((MapMath.normalizeLongitude(longitude) + Math.PI) * 30.0 / Math.PI) + 1;
    if (zone < 1)
      zone = 1;
    else if (zone > 60)
      zone = 60;
    return zone;
  }

  private static final double FC1 = 1.0;
  private static final double FC2 = 0.5;
  private static final double FC3 = 0.16666666666666666666;
  private static final double FC4 = 0.08333333333333333333;
  private static final double FC5 = 0.05;
  private static final double FC6 = 0.03333333333333333333;
  private static final double FC7 = 0.02380952380952380952;
  private static final double FC8 = 0.01785714285714285714;

  private final double esp;
  private final double ml0;
  private final double[] en;

  private final double projectionLatitude, projectionLongitude;
  private final double scaleFactor;
  private final double falseEasting, falseNorthing;

  private final Earth ellipsoid;
  private final double es; // earth.getEccentricitySquared
  private final double totalScale; // scale to convert cartesian coords in km
  private final boolean spherical;

  public TransverseMercatorProjection() {
    this(EarthEllipsoid.WGS84, 0, 0, 0.9996, 0, 0);
  }

  public TransverseMercatorProjection(int zone) {
    this(EarthEllipsoid.WGS84, Math.toDegrees(((zone -1) + .5) * Math.PI / 30. - Math.PI), 0, 0.9996, 500000, 0);
  }

  /**
   * Set up a projection suitable for State Plane Coordinates.
   * Best used with earth ellipsoid and false-easting/northing in km
   */
  public TransverseMercatorProjection(Earth ellipsoid, double lon_0_deg, double lat_0_deg, double k, double falseEast, double falseNorth) {
    super("TransverseMercatorProjection", false);
    this.ellipsoid = ellipsoid;
    projectionLongitude = Math.toRadians(lon_0_deg);
    projectionLatitude = Math.toRadians(lat_0_deg);
    scaleFactor = k;
    falseEasting = falseEast;
    falseNorthing = falseNorth;

    this.es = ellipsoid.getEccentricitySquared();
    this.spherical = ellipsoid.isSpherical();
    this.totalScale = ellipsoid.getMajor() * .001; // scale factor for cartesion coords in km.

    if (spherical) {
      en = null;
      esp = scaleFactor;
      ml0 = .5 * esp;
    } else {
      en = MapMath.enfn(es);
      ml0 = MapMath.mlfn(projectionLatitude, Math.sin(projectionLatitude), Math.cos(projectionLatitude), en);
      esp = es / (1. - es);
    }

    // parameters
    addParameter(CF.GRID_MAPPING_NAME, CF.TRANSVERSE_MERCATOR);
    addParameter(CF.LONGITUDE_OF_CENTRAL_MERIDIAN, lon_0_deg);
    addParameter(CF.LATITUDE_OF_PROJECTION_ORIGIN, lat_0_deg);
    addParameter(CF.SCALE_FACTOR_AT_CENTRAL_MERIDIAN, scaleFactor);
    if ((falseEasting != 0.0) || (falseNorthing != 0.0)) {
      addParameter(CF.FALSE_EASTING, falseEasting);
      addParameter(CF.FALSE_NORTHING, falseNorthing);
      addParameter(CDM.UNITS, "km");
    }
    addParameter(CF.SEMI_MAJOR_AXIS, ellipsoid.getMajor());
    addParameter(CF.INVERSE_FLATTENING, 1.0 / ellipsoid.getFlattening());
  }

  private ProjectionPoint project(double lplam, double lpphi) {
    if (spherical) {
      double cosphi = Math.cos(lpphi);
      double b = cosphi * Math.sin(lplam);

      double x = ml0 * scaleFactor * Math.log((1. + b) / (1. - b));
      double ty = cosphi * Math.cos(lplam) / Math.sqrt(1. - b * b);
      ty = MapMath.acos(ty);
      if (lpphi < 0.0)
        ty = -ty;
      double y = esp * (ty - projectionLatitude);
      return ProjectionPoint.create(x, y);

    } else {
      double al, als, n, t;
      double sinphi = Math.sin(lpphi);
      double cosphi = Math.cos(lpphi);
      t = Math.abs(cosphi) > 1e-10 ? sinphi / cosphi : 0.0;
      t *= t;
      al = cosphi * lplam;
      als = al * al;
      al /= Math.sqrt(1. - es * sinphi * sinphi);
      n = esp * cosphi * cosphi;
      double x = scaleFactor * al * (FC1 + FC3 * als * (1. - t + n
          + FC5 * als * (5. + t * (t - 18.) + n * (14. - 58. * t) + FC7 * als * (61. + t * (t * (179. - t) - 479.)))));
      double y = scaleFactor * (MapMath.mlfn(lpphi, sinphi, cosphi, en) - ml0
          + sinphi * al * lplam * FC2 * (1. + FC4 * als * (5. - t + n * (9. + 4. * n) + FC6 * als
              * (61. + t * (t - 58.) + n * (270. - 330 * t) + FC8 * als * (1385. + t * (t * (543. - t) - 3111.))))));
      return ProjectionPoint.create(x, y);
    }
  }

  private ProjectionPoint projectInverse(double x, double y) {
    if (spherical) {
      double h = Math.exp(x / scaleFactor);
      double g = .5 * (h - 1. / h);
      h = Math.cos(projectionLatitude + y / scaleFactor);
      double outy = MapMath.asin(Math.sqrt((1. - h * h) / (1. + g * g)));
      if (y < 0)
        outy = -outy;
      double outx = Math.atan2(g, h);
      return ProjectionPoint.create(outx, outy);

    } else {
      double n, con, cosphi, d, ds, sinphi, t;

      double outx = 0;
      double outy = MapMath.inv_mlfn(ml0 + y / scaleFactor, es, en);
      if (Math.abs(y) >= MapMath.HALFPI) {
        outy = y < 0. ? -MapMath.HALFPI : MapMath.HALFPI;
        return ProjectionPoint.create(outx, outy);

      } else {
        sinphi = Math.sin(outy);
        cosphi = Math.cos(outy);
        t = Math.abs(cosphi) > 1e-10 ? sinphi / cosphi : 0.;
        n = esp * cosphi * cosphi;
        d = x * Math.sqrt(con = 1. - es * sinphi * sinphi) / scaleFactor;
        con *= t;
        t *= t;
        ds = d * d;
        outy -= (con * ds / (1. - es)) * FC2
            * (1. - ds * FC4
                * (5. + t * (3. - 9. * n) + n * (1. - 4 * n) - ds * FC6 * (61. + t * (90. - 252. * n + 45. * t)
                    + 46. * n - ds * FC8 * (1385. + t * (3633. + t * (4095. + 1574. * t))))));
        outx = d * (FC1 - ds * FC3 * (1. + 2. * t + n - ds * FC5
            * (5. + t * (28. + 24. * t + 8. * n) + 6. * n - ds * FC7 * (61. + t * (662. + t * (1320. + 720. * t))))))
            / cosphi;
        return ProjectionPoint.create(outx, outy);
      }
    }
  }

  public boolean isRectilinear() {
    return false;
  }
  public boolean hasInverse() {
    return true;
  }

  @Override
  public String getProjectionTypeLabel() {
    return "Transverse Mercator Ellipsoidal Earth";
  }

  @Override
  public Projection constructCopy() {
    return new TransverseMercatorProjection(ellipsoid, Math.toDegrees(projectionLongitude),
        Math.toDegrees(projectionLatitude), scaleFactor, falseEasting, falseNorthing);
  }

  @Override
  public String paramsToString() {
    Formatter f = new Formatter();
    f.format("origin lat,lon=%f,%f scale=%f earth=%s falseEast/North=%f,%f", Math.toDegrees(projectionLatitude),
        Math.toDegrees(projectionLongitude), scaleFactor, ellipsoid, falseEasting, falseNorthing);
    return f.toString();
  }

  @Override
  public ProjectionPoint latLonToProj(LatLonPoint latLon) {
    double fromLat = Math.toRadians(latLon.getLatitude());
    double theta = Math.toRadians(latLon.getLongitude());
    if (projectionLongitude != 0) {
      theta = MapMath.normalizeLongitude(theta - projectionLongitude);
    }

    ProjectionPoint res = project(theta, fromLat);
    return ProjectionPoint.create(totalScale * res.getX() + falseEasting, totalScale * res.getY() + falseNorthing);
  }

  @Override
  public LatLonPoint projToLatLon(ProjectionPoint world) {
    double fromX = (world.getX() - falseEasting) / totalScale; // assumes cartesian coords in km
    double fromY = (world.getY() - falseNorthing) / totalScale;

    ProjectionPoint dst = projectInverse(fromX, fromY);
    double toLon = dst.getX();
    double toLat = dst.getY();
    if (toLon < -Math.PI)
      toLon = -Math.PI;
    else if (toLon > Math.PI)
      toLon = Math.PI;
    if (projectionLongitude != 0)
      toLon = MapMath.normalizeLongitude(toLon) + projectionLongitude;

    return LatLonPoint.create(Math.toDegrees(toLat), Math.toDegrees(toLon));
  }

  @Override
  public boolean crossSeam(ProjectionPoint pt1, ProjectionPoint pt2) {
    // TODO: check, taken from ucar.unidata.geoloc.projection.TransverseMercator
    // either point is infinite
    if (LatLonPoints.isInfinite(pt1) || LatLonPoints.isInfinite(pt2)) {
      return true;
    }

    double y1 = pt1.getY() - falseNorthing;
    double y2 = pt2.getY() - falseNorthing;

    // opposite signed long lines
    return (y1 * y2 < 0) && (Math.abs(y1 - y2) > 2 * ellipsoid.getMajor());
  }

  @Override
  public boolean equals(Object o) {
    if (this == o)
      return true;
    if (o == null || getClass() != o.getClass())
      return false;

    TransverseMercatorProjection that = (TransverseMercatorProjection) o;
    // if ((this.getDefaultMapArea() == null) != (that.defaultMapArea == null)) return false; // common case is that
    // these are null
    // if (this.getDefaultMapArea() != null && !this.defaultMapArea.equals(that.defaultMapArea)) return false;

    if (Double.compare(that.falseEasting, falseEasting) != 0)
      return false;
    if (Double.compare(that.falseNorthing, falseNorthing) != 0)
      return false;
    if (Double.compare(that.projectionLatitude, projectionLatitude) != 0)
      return false;
    if (Double.compare(that.projectionLongitude, projectionLongitude) != 0)
      return false;
    if (Double.compare(that.scaleFactor, scaleFactor) != 0)
      return false;
    return ellipsoid.equals(that.ellipsoid);

  }

  @Override
  public int hashCode() {
    int hash = 5;
    hash = 97 * hash + (int) (Double.doubleToLongBits(this.projectionLatitude)
        ^ (Double.doubleToLongBits(this.projectionLatitude) >>> 32));
    hash = 97 * hash + (int) (Double.doubleToLongBits(this.projectionLongitude)
        ^ (Double.doubleToLongBits(this.projectionLongitude) >>> 32));
    hash = 97 * hash
        + (int) (Double.doubleToLongBits(this.scaleFactor) ^ (Double.doubleToLongBits(this.scaleFactor) >>> 32));
    hash = 97 * hash
        + (int) (Double.doubleToLongBits(this.falseEasting) ^ (Double.doubleToLongBits(this.falseEasting) >>> 32));
    hash = 97 * hash
        + (int) (Double.doubleToLongBits(this.falseNorthing) ^ (Double.doubleToLongBits(this.falseNorthing) >>> 32));
    hash = 97 * hash + (this.ellipsoid != null ? this.ellipsoid.hashCode() : 0);
    return hash;
  }
}

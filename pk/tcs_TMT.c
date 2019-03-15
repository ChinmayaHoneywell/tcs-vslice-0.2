#include <stdio.h>
#include <ctype.h>
#include <string.h>
#include <slalib.h>
#include "tcs.h"
#include "tcsmac.h"

double Wait ( );

int main ( )

/*
**  - - - - - -   -
**   T C S _ T M T
**  - - - - - - - -
**
**  Demonstration of TCSpk pointing kernel using the higher-level
**  components of the API and an essentially single-thread design.  The
**  default settings are for the TMT, an altazimuth mount with a bent-
**  Nasmyth instrument rotator and three autoguiders.
**
**  ----------------
**  REAL-TIME DESIGN
**  ----------------
**
**  TCSpk is designed for TCS applications that are highly efficient
**  computationally and that separate the more time-critical foreground
**  "tracking" computations (typically a 20 Hz loop) from the lower
**  priority background computations that maintain a sufficiently
**  up-to-date description of what is happening (i.e. the so-called
**  "data context".  However, in cases where CPU power is abundant and
**  timing requirements fairly relaxed, simplicity of design may in fact
**  be the overriding consideration.  The present demonstration has such
**  cases in mind.
**
**  The data context is an off-the-peg one, hidden inside a CTX data
**  structure and maintained using a single-thread architecture and
**  calls to TCSpk's simplified "LiteAPI".  The lower-level CoreAPI is
**  not used, except for demonstration purposes in one special case.
**  Use of the Simple API may, depending on the needs of the TCS
**  concerned, be at the expense of some wasted computation, together
**  with slightly uneven delivery of encoder demands.
**
**  The timing irregularities are in fact quite usual in a modern TCS.
**  The pointing kernel is regarded as "soft real time", supplying time-
**  stamped samples of the desired encoder positions for approximately
**  the present time.  A separate "hard real time" system with access to
**  accurate absolute time can then interpolate/extrapolate to obtain
**  the stream of positions used by the servo systems.  The end result
**  is that the desired loci are realized despite the less than precise
**  timing in the pointing kernel itself.
**
**  ---------------
**  EXTERNAL EVENTS
**  ---------------
**
**  Here is a summary of what actions the TCS application needs to take
**  when various external events occur.
**
**   EVENT                        ACTION
**
**   Start-up                     Make arrangements to read the achieved
**                                rotator angle and velocity, and
**                                initialization the context, choosing
**                                update intervals for the target-
**                                independent and target-dependent
**                                context items (typically 60s and 5s
**                                respectively).  Establish the means of
**                                calling the tcsCtick function at
**                                regular intervals of typically 50 ms.
**
**   User asks for a different    Obtain the new information and insert
**   position-angle.              it into the context ready for the next
**                                clock tick.
**
**   User asks for a different    Obtain the new information and insert
**   pointing-origin.             it into the context ready for the next
**                                clock tick.
**
**   User updates the pointing-   Obtain the new information and insert
**   origin or the associated     it into the context ready for the next
**   offsets from base            clock tick.
**
**   User supplies a new          Obtain the new information and insert
**   target.                      it into the context ready for the next
**                                clock tick.  It is usually appropriate
**                                to zero the guiding corrections at
**                                this stage.
**
**   User updates the target      Obtain the new information and insert
**   offsets from base or         it into the context ready for the next
**   differential tracking        clock tick.  If the offset is big
**   rates.                       enough, a refinement is to call
**                                tcsCupd to refresh the target-
**                                dependent context items.
**
**   Guiding activity occurs.     Calculate the guiding corrections and
**                                insert them into the context.
**
**   Weather readings change.     Obtain the new information and insert
**                                it into the context ready for the next
**                                clock tick.
**
**   Every 50ms (or so).          Call tcsCtick to make any timely
**                                updates to the context and to
**                                calculate new mount, M3 and rotator
**                                positions.
**
**  -----------------
**  DATA REQUIREMENTS
**  -----------------
**
**  Here is a summary of the information required to drive the TCS.
**
**   INITIALIZATION
**
**   . Site longitude, latitude and height above sea level.
**
**       Inaccurate site coordinates will be manifested as spurious
**       polar/azimuth/roll axis misalignment parameters.  If
**       grossly wrong, the refraction predictions will be
**       unreliable.  Height makes little difference (but air
**       pressure does - see below).
**
**   . Telescope focal length.
**
**       If the TCS does not support the pointing-origin feature,
**       this can be set to any sensible value (say 1.0).
**
**   . UT1-UTC.
**
**       Zero is acceptable on equatorial telescopes that do not
**       have absolute encoding in RA.  The omission will be
**       canceled out at sync time.
**
**   . TAI-UTC.
**
**       Zero is acceptable on equatorial telescopes that do not
**       have absolute encoding in RA.  The omission will be
**       canceled out at sync time, except for second-order
**       effects.
**
**   . TT-TAI.
**
**       Always 32.184s.
**
**   . Polar motion.
**
**       Zero will do on all but the most accurate telescopes.
**
**   . Refraction data.
**
**       Temperature, pressure and relative humidity are all
**       required, to whatever accuracy they are available.
**       Pressure is the most critical one, and high-accuracy
**       telescopes need it to 1 HPa (=mB).  A fixed value for
**       the tropospheric lapse rate of 0.0065 K/m is always
**       acceptable.  For radio applications high-accuracy
**       temperature and humidity are also needed.  Finally,
**       for optical/IR applications, a reference wavelength
**       needs to be chosen, for example 0.5 micrometers.
**
**   . Mount type.
**
**       ALTAZ, EQUAT or GIMBAL needs to be specified.  If GIMBAL,
**       there are numerical parameters as well, to specify the
**       gimbal orientation.
**
**   . Rotator location.
**
**       Use OTA for ordinary non-Nasymyth, non-coude applications,
**       or where there is no rotator.  In this special TMT version use
**       the generalized GENRO option.
**
**   . Zenith avoidance distance.
**
**       Tracks through the zenith will be diverted to comply with
**       this parameter.
**
**   . Reference systems.
**
**       Reference systems for controlling the telescope and rotator
**       must be specified.  FK5 is the sensible default.  If it is a
**       sort of mean RA,Dec, an equinox is needed:  2000.0 is the usual
**       choice.  A wavelength has to be chosen as well, perhaps
**       0.55 micrometers for visual use.  (Note that ICRS coordinates
**       are the same as FK5 2000 coordinates to the accuracy required
**       here.)
**
**   . Pointing model.
**
**       A pointing model is required, supplied as the file that is
**       written by the TPOINT application when the OUTMOD command is
**       executed.  It is possible to change individual coefficient
**       values while running.
**
**   . Rotator readings.
**
**       The system needs a rotator angle, plus a velocity
**       and timestamp.  They are initially set to zero, and simple
**       equatorial applications where there is no rotator need take no
**       further action.
**
**   REFRACTION DATA
**
**   . Meteorological readings plus color.
**
**       The temperature, pressure, humidity, lapse rate and
**       reference wavelength can be changed at any time.  On small
**       telescopes of modest accuracy, though it is worth getting a
**       pressure reading it is unlikely to need changing during the
**       night.  If a pressure reading is unavailable, it can be
**       estimated from the observatory height above sea level using a
**       standard atmosphere model.
**
**   FIELD ORIENTATION
**
**   This area can be ignored if there is no rotator.  If there is a
**   rotator, the following can all be changed while running, as
**   required.
**
**   . Instrument Alignment Angle.
**
**       The IAA is nominated by the user.  It can be changed at any
**       time but is likely to remain fixed for the whole session.
**
**   . Instrument Position Angle.
**
**       The user may wish to change the angle on the sky of the
**       projection of his instrument.  On each occasion, all that is
**       needed is the new angle.  The angle can be changed while
**       tracking a target.
**
**   . De-rotation criterion.
**
**       The rotator demands that are generated can either freeze
**       the IPD on the sky ("slit-optimized") or can eliminate
**       any overall rotation component ("field-optimized").
**
**   POINTING ORIGIN
**
**   Small-telescope applications may omit the entire pointing-
**   origin feature, though it can be useful if both a viewing
**   eyepiece and a CCD are used - the star can be slewed into
**   the eyepiece, centered, and then sent to the CCD center
**   simply by changing the x,y.  The initialization procedure leaves
**   the pointing on-axis.  During running, the following can all be
**   changed as required.
**
**   . Base pointing-origins (NPOS of them).
**
**       The pointing-origin should be set to 0,0 by default,
**       corresponding to the rotator axis (if there is a
**       rotator).  Changing the x,y while tracking will cause
**       the star image to move to the specified location.
**
**   . Offsets from base in x,y (three for each base; they add).
**
**       This is an advanced feature, used for such things
**       as trailing the image along a spectrograph slit.
**       The offsets should be left at zero if the feature
**       is unwanted.
**
**   . Pointing-origin selection.
**
**       This integer, in the range 0 to NPOS-1, selects which
**       of the NPOS pointing-origins is to be used.  The target
**       image will then transfer to this place in the focal
**       plane automatically.
**
**   REFERENCE SYSTEMS AND TARGET
**
**   The coordinate systems for controlling the mount and the
**   instrument rotator are individually controllable.  The minimum
**   implementation would be a fixed system of FK5/2000 (effectively the
**   same thing as Hipparcos/ICRS) for the mount and the same for the
**   rotator.  The standard context includes four additional reference
**   systems, three for guide stars and one an auxiliary system for the
**   mount target.  The latter can be used for such things as offsets
**   in az/el.
**
**   Although a fixed reference system can be used, all TCS applications
**   require the ability to change the target coordinates of course.
**
**   . Mount reference system (and equinox if needed).
**
**        FK5/2000 can be used for almost everything.  For
**        tracking planets, geocentric apparent place may
**        also be needed, depending on how the ephemeris
**        data are being calculated.
**
**   . Color for mount and rotator tracking.
**
**        This allows atmospheric dispersion to be corrected.
**        On small telescopes leave it set to (say)
**        0.55 micrometers.
**
**   . Rotator reference system (and equinox if needed).
**
**        This controls which sort of "north" is being used.
**
**   . Target coordinates.
**
**        The target RA,Dec (or Az,El) can be changed at any
**        time, on their own, causing the telescope to move
**        to the new coordinates.  Various other actions may
**        be required at the same time, for example resetting
**        the offsets from base, reverting to the rotator-
**        axis pointing-origin, resetting the guiding
**        corrections and so on.  It is up to the TCS designer
**        to decide.
**
**   . Differential rates and reference time.
**
**        These allow solar-system objects to be tracked.  The
**        reference time is the TAI MJD at which the supplied
**        RA,Dec was, or will be, correct.
**
**   . Offsets from base.
**
**        These allow advanced features such as scan patterns
**        to be implemented.  It is better to use the offsets
**        than the target coordinates, because the original
**        (base) target can be recovered simply by resetting
**        the offsets to zero.
**
**  VIRTUAL TELESCOPES
**
**  TCSpk is based on a concept called the "virtual telescope" (VT).
**  This is a set of data and transformations that link a place in the
**  sky, called the "target", with a place in the focal plane, called
**  the "pointing origin", and with a mechanical pointing direction for
**  the telescope.
**
**  Complicated details such as pointing corrections are hidden inside
**  the virtual-telescope functions, so that clients (for example an
**  instrument wishing to offset the image in the focal plane) can do so
**  simply by adjusting the star or image coordinates.  A TCS always
**  supports at least one virtual telescope, that for the telescope
**  mount itself.  However, there may also be guiders, tip/tilt sub-
**  reflectors, and choppers present, and these can be implemented as
**  separate virtual telescopes, albeit ones which are forced to share
**  the one mount position (because there is only one mount).
**
**  In the case of the mount virtual telescope, tracking demands are
**  obtained by transforming the target position through a series of
**  intermediate states into encoder coordinates.  The transformations
**  require knowledge of site location, time, weather, pointing errors
**  and the orientation of the instrument rotator.  The following sets
**  of coordinates are involved, in this and any other virtual
**  telescopes that the TCS is using:
**
**    Label            Meaning
**
**    TARGET           Where in the sky is to be imaged.  This is
**                     typically an RA,Dec (usually a J2000/ICRS RA,Dec)
**                     but topocentric Az/El is also supported.
**
**    POINTING-ORIGIN  The x,y in the focal plane which is to
**                     receive the image of the target.
**
**    ENCODER          The encoder readings that the servos are
**                     instructed to realize.
**
**  The TCSpk package includes a powerful set of low level functions
**  (the "Core API" mentioned earlier) that transform between these
**  coordinates (and other internal ones) in various ways.  These
**  support a wide variety of TCS designs that can include multi-
**  threaded or even multi-processor architectures and that achieve very
**  high levels of computational efficiency.  The "Simple API" used by
**  the present demonstration is a set of higher-level interfaces that
**  are intended to be easier to use.  These functions are based on a
**  single-thread architecture and two convenient data structures, one
**  (CTX) containing a standard context (the set of data items that
**  the pointing-kernel maintains) and the other (TELV) the data for a
**  virtual telescope.
**
**  The functions that support the CTX/TELV structures, i.e. those that
**  make up the Simple API, are as follows:
**
**    tcsCaux      get/put a pointing-model auxiliary reading
**    tcsCbp       update mount configuration (beyond zenith etc.)
**    tcsCdome     predict dome pointing
**    tcsCguide    guiding-related actions
**    tcsCinit     initialize the standard pointing kernel context
**    tcsCipa      change instrument position angle
**    tcsCmet      change the meteorological readings
**    tcsCmod      get/put the internal pointing model
**    tcsCpo       pointing-origin-related actions
**    tcsCrot      change rotator control parameters
**    tcsCrota     update achieved rotator angle and velocity
**    tcsCrs       change one of the astrometric reference systems
**    tcsCtar      target-related actions
**    tcsCtick     per-clock-tick processing, run at say 20 Hz
**    tcsCupd      perform SLOW and/or FAST updates
**    tcsCvt       extract a TELV structure from a CTX structure
**    tcsCypa      P.A. of rotator y-axis in the three CTX systems
**    tcsTVinit    create a TELV structure from scratch
**    tcsTVgetm    from a TELV structure extract the mount coordinates
**    tcsTVgetp    from a TELV structure extract the pointing-origin
**    tcsTVgett    from a TELV structure extract the target
**    tcsTVputm    in a TELV structure replace the mount coordinates
**    tcsTVputp    in a TELV structure replace the pointing-origin
**    tcsTVputt    in a TELV structure replace the target
**    tcsTVsolm    solve a TELV structure for mount coordinates
**    tcsTVsolp    solve a TELV structure for x,y
**    tcsTVsolt    solve a TELV structure for RA/Dec (etc.)
**    tcsYpa       P.A. of rotator y-axis
**
**  The tcsCtick function is responsible for housekeeping at all levels
**  of urgency (in TCSpk parlance SLOW and MEDIUM as well as FAST).  The
**  SLOW and MEDIUM updates happen at preset regular intervals, but the
**  application can override this by calling the tcsCupd function to
**  bring forward the required update(s).  The functions tcsCmod,
**  tcsCrot, tcsCrs and tcsCtar all schedule a MEDIUM update
**  automatically, while tcsCmet schedules a full SLOW-then-MEDIUM
**  update.
**
**  Last revision:   23 September 2018
**
**  Copyright P.T.Wallace.  All rights reserved.
*/

{

/* Context */
   CTX pk;

/* Virtual Telescope */
   TELV vt;

   TPMOD pm;
   PRF rfun;
   MTYPE mount;
   ROTLOC locr;
   ASTROM ast;
   int i, n, chorpa, j, npo, nob, ipo, i4[4], jbp;
   char sterm[9], s;
   char* tpfile;
   double tai, uslo, umed, delat, delut, xpmr, ypmr, elon, phi, hm,
          wavelr, wavel, tc, pmb, rh, tlr, fl, gim1z, gim2y, gim3x,
          fa, fb, iba, iea, rnogo, vterm, da, db, ga, gb, x, y,
          dx, dy, ra, dec, w1, w2, dra, ddec, rat, dect, dema, demb,
          demrM3, demtM3, dpa, demr, a1, b1, a2, b2, a, b, az, el,
          ypam, ypar, ypau, rg, dg, xmm, ymm, xmmm, ymmm, xm, ym, r;


/*
** --------------------------------------
** Initialize the pointing kernel context
** --------------------------------------
**
** The following code uses literal values.  An operational TCS would
** normally be driven by an external agent such as a configuration file.
**
*/

/* TAI (from an internal clock simulator). */
   tai = Wait ();

/* SLOW and MEDIUM update intervals (s). */
   uslo = 10.0;
   umed = 1.0;

/* TAI-UTC and UT1-UTC (s). */
   delat = 37.0;
   delut = 0.746;

/* Polar motion (radians). */
   xpmr = 0.25*AS2R;
   ypmr = 0.4*AS2R;

/* East longitude and geodetic latitude (radians). */
   elon = -155.4816*D2R;
   phi = 19.8327*D2R;

/* Height above sea level (m). */
   hm = 4050.0;

/* Reference and operational wavelengths (micron). */
   wavelr = 0.5;
   wavel = 1.0;

/* Special refraction function. */
   rfun = NULL;

/* Temperature (C), pressure (hPa) and humidity (1.0 = 100%). */
   tc = 2.25;
   pmb = 610.0;
   rh = 0.5;

/* Tropospheric lapse rate (K/m). */
   tlr = 0.0065;

/* Telescope focal length (mm). */
   fl = 450000.0;

/* Mount type. */
   mount = ALTAZ;

/* Euler angles (radians, generalized gimbal case only). */
   gim1z = 0.0;
   gim2y = 0.0;
   gim3x = 0.0;

/* Rotator location code, and effect of roll and pitch. */
   locr = GENRO;
   fa = 0.0;
   fb = 0.0;

/* IBA and IEA for NFIRAOS. */
   iba = 174.5*D2R;
   iea = 0.0*D2R;

/* Pole avoidance distance (radians). */
   rnogo = 0.25*D2R;

/* Name of TPOINT model file. */
   tpfile = "altaz.mod";

/* Initialize the pointing-kernel context structure. */
   if ( tcsCinit ( &pk, tai, uslo, umed, delat, delut, xpmr, ypmr, elon,
                   phi, hm, wavelr, rfun, tc, pmb, rh, tlr, fl, mount,
                   gim1z, gim2y, gim3x, locr, fa, fb, iba, iea, rnogo,
                   tpfile ) ) {
      printf ( "Context initialization has failed: aborting!\n" );
      return -1;
   }

/*
** -----------------------------------------------------
** Demonstrate how to change the telescope configuration
** -----------------------------------------------------
*/

   fb = 1.0;
   if ( tcsItel ( &pk.tsite, fl, mount, gim1z, gim2y, gim3x, locr,
                  fa, fb, iba, iea, rnogo, &pk.tel ) ) return -1;

/*
** ------------------------------------------------
** Demonstrate direct actions on the pointing model
** ------------------------------------------------
**/

/* Copy the TPOINT model from the context. */
   if ( tcsCmod ( &pk, PK_GET, &pm ) ) return -1;

/* Demonstrate changing a coefficient value using a CoreAPI function. */
   if ( tcsSterm ( "AW", -5.0*AS2R, &pm ) ) return -1;

/* List the model, again using a CoreAPI function. */
   printf ( "\nPointing model:\n\n" );
   for ( i = 1; ! tcsQterm ( i, &pm, sterm, &vterm ); i++ ) {
      chorpa = islower(sterm[0]) ? '&' : ' ';
      if ( chorpa == '&' ) {
         n = (int) strlen ( sterm );
         for ( j = 0; j < n; j++ ) {
            sterm[j] = (char) toupper ( (int) sterm[j] );
         }
      }
      printf ( "%3d  %c %-8s%+10.2f\n", i, chorpa, sterm, vterm/AS2R );
   }

/* Use another CoreAPI function to look up a named term. */
   strncpy ( sterm, "AW", 9 );
   j = tcsQnatrm ( sterm, &pm, &vterm, &i );
   if ( ! j )
      printf ( "\nTerm %s is number %d and has amplitude %g arcsec.\n",
               sterm, i, vterm/AS2R );

/* Insert the updated TPOINT model into the context. */
   if ( tcsCmod ( &pk, PK_PUT, &pm ) ) return -1;

/*
** ----------------------------------------------
** Demonstrate how to update the weather readings
** ----------------------------------------------
*/

   tc = 1.85;
   pmb = 610.0;
   rh = 0.2;
   tlr = 0.0065;
   if ( tcsCmet ( &pk, tc, pmb, rh, tlr ) ) return -1;

/*
** ---------------------------------------------
** Demonstrate how to apply a guiding correction
** ---------------------------------------------
*/

/* The guiding correction we are going to apply. */
   da = 0.0*AS2R;
   db = 22.0*AS2R;

/* Apply it. */
   if ( tcsCguide ( &pk,
                    PK_GTO,    /* action code */
                    tai,       /* TAI */
                    da, db,    /* new guiding corrections */
                    &ga, &gb   /* resulting corrections */
                  ) ) {
      printf ( "Guiding error: aborting!\n" );
      return -1;
   }

/*
** --------------------------------------------
** Demonstrate how to control field orientation
** --------------------------------------------
*/

/* Change the rotator control parameters. */
   if ( tcsCrot ( &pk,
                  FK5,         /* type of coordinate system */
                  2000.0,      /* equinox */
                  1.0,         /* wavelength (micrometres) */
                  0.0,         /* IAA */
                  0.0,         /* IPA */
                  FIELDO       /* field-optimized */
                ) ) {
      printf ( "Rotator setup error: aborting!\n" );
      return -1;
   }

/* Change the instrument position angle alone. */
   if ( tcsCipa ( &pk, 0.0 ) ) {
      printf ( "Position-angle error: aborting!\n" );
      return -1;
   }

/*
** ---------------------------------------------
** Demonstrate how to change the pointing-origin
** ---------------------------------------------
**
** The pointing kernel context contains NPOS pointing-origins and the
** means to select one of them.  By convention, #0 is the rotator axis,
** where x and y are zero, and the others are used to select
** significant places in the focal plane:  for example #1 might be the
** the center of the acquisition camera and #2 could be the entrance
** aperture of the instrument.  Each pointing-origin comprises a base
** (x,y) and three sets of additive (dx,dy) "offsets from base".
*/

/* The pointing-origin and offsets-from-base we will use. */
   npo = 1;
   nob = 0;
   x = 180.0;
   y = -150.0;
   dx = -1.0;
   dy = -2.0;

/* Specify the P.O.'s base position and zero the offsets. */
   if ( tcsCpo ( &pk, PK_PXY, npo, 0, x, y, &ipo, &x, &y ) ) {
      printf ( "Pointing-origin error (PXY): aborting!\n" );
      return -1;
   }

/* Change the specified set of offsets from base. */
   if ( tcsCpo ( &pk, PK_POB, npo, nob, dx, dy, &ipo, &x, &y ) ) {
      printf ( "Pointing-origin error (POB) error: aborting!\n" );
      return -1;
   }

/* Select the P.O. */
   if ( tcsCpo ( &pk, PK_PN, npo, 0, 0.0, 0.0, &ipo, &x, &y ) ) {
      printf ( "Pointing-origin error (PN) error: aborting!\n" );
      return -1;
   }

/*
** --------------------------------------------
** Demonstrate how to change the science target
** --------------------------------------------
*/

/* Astrometry for mount control. */
   if ( tcsCrs ( &pk, PK_M, FK5, 2000.0, wavel ) ) return -1;

/* Use the same system for rotator control. */
   if ( tcsCrs ( &pk, PK_R, FK5, 2000.0, wavel ) ) return -1;

/* Change RA,Dec and zero the offsets and rates. */
   ra = 99.0775855657937541*D2R;
   dec = 28.3621294891994005*D2R;
   if ( tcsCtar ( &pk, PK_TBA, PK_M, 0, 0.0, tai, ra, dec,
                  &ast, &w1, &w2  ) ) return -1;

/* Offsets from base (the first set). */
   nob = 0;
   dra = 15.0*AS2R;
   ddec = 60.0*AS2R;
   if ( tcsCtar ( &pk, PK_TOB, PK_M, nob, 0.0, tai, dra, ddec,
                  &ast, &rat, &dect ) ) return -1;

/* Preempt the FAST processing. */
   pk.m_tara = rat;
   pk.m_tarb = dect;

/* Report the target. */
   printf ( "\nScience target:\n" );
   slaDr2tf ( 4, slaDranrm(ra), &s, i4 );
   printf ( "\nRA = %2.2i %2.2i %2.2i.%4.4i  ",
                                           i4[0], i4[1], i4[2], i4[3] );
   slaDr2af ( 3, dec, &s, i4 );
   printf ( "Dec = %c%2.2i %2.2i %2.2i.%3.3i  J2000.0  <- base\n",
                                        s, i4[0], i4[1], i4[2], i4[3] );
   slaDr2tf ( 4, slaDrange(rat-ra), &s, i4 );
   printf ( "    %c%2.2i %2.2i %2.2i.%4.4i  ",
                                        s, i4[0], i4[1], i4[2], i4[3] );
   slaDr2af ( 3, dect-dec, &s, i4 );
   printf ( "      %c%2.2i %2.2i %2.2i.%3.3i           <- offset\n",
                                        s, i4[0], i4[1], i4[2], i4[3] );

/*
** --------------------------------------
** Demonstrate how to change a guide star
** --------------------------------------
**
** The example is 94 Psc.
*/

/* Astrometry for guide star A. */
   if ( tcsCrs ( &pk, PK_A, FK5, 2000.0, 1.0 ) ) return -1;

/* Change RA,Dec and zero the offsets and rates. */
   ra = 99.027155381762455*D2R;
   dec = 28.437241968719086*D2R;
   if ( tcsCtar ( &pk, PK_TBA, PK_A, 0, 0.0, tai, ra, dec,
                  &ast, &w1, &w2  ) ) return -1;

/* Preempt the FAST processing. */
   pk.a_tara = w1;
   pk.a_tarb = w2;

/* Report the guide star. */
   printf ( "\nGuide star A:\n" );
   slaDr2tf ( 4, slaDranrm(ra), &s, i4 );
   printf ( "\nRA = %2.2i %2.2i %2.2i.%4.4i  ",
                                           i4[0], i4[1], i4[2], i4[3] );
   slaDr2af ( 3, dec, &s, i4 );
   printf ( "Dec = %c%2.2i %2.2i %2.2i.%3.3i  J2000.0\n",
                                        s, i4[0], i4[1], i4[2], i4[3] );
   slaDr2tf ( 4, slaDrange ( w1 - ra ), &s, i4 );

/*
** --------------------------------------------------------------
** Demonstrate how to set the "user" astrometric reference system
** --------------------------------------------------------------
*/

/* Specify observed az/el for the auxiliary system. */
   if ( tcsCrs ( &pk, PK_U, AZEL_OBS, 0.0, wavel ) ) return -1;

/*
** -------------------------------------------------
** Demonstrate how to change the mount configuration
** -------------------------------------------------
**
**  In general, any accessible target can be acquired in either of two
**  mount attitudes.  Depending on the type of mount, these correspond
**  to above or below the pole, nearside or farside of the zenith, and
**  so on.
**
**  For the sake of demonstration, the normal "nearside of zenith"
**  configuration is selected.
*/

   jbp = 0;
   if ( tcsCbp ( &pk, jbp ) ) return -1;

/*
** -----------------------------------
** Demonstrate the per-tick processing
** -----------------------------------
**
** It is assumed here that the appropriate mechanisms are in place
** to trigger processing once per tick while at the same time being
** (a) in a position to service user-interface and observing-sequencer
** requests for new targets etc. and (b) to retrieve information from
** the mount and rotator controllers as required.
*/

/* Simulate a few successive calls to FAST at 20Hz. */
   printf ( "\nTracking:\n" );
   for ( i = -20; i < 3; i++ ) {

   /* For the first few, trigger a MEDIUM update. */
      if ( i < 0 ) (void) tcsCupd( &pk, PK_MED );

   /* Wait for the next tick. */
      tai = Wait ();

   /*
   ** Note:  depending both on the TCS design and whether an instrument
   ** rotator is in use, it may be appropriate at this point to make
   ** changes to the context that tell the FAST processing that the
   ** rotator has moved.  When to do so depends on whether the rotator
   ** is being driven and the current speed at which it is moving.  The
   ** FAST processing in tcsCtick will linearly extrapolate the achieved
   ** rotator position, and frequent updates of the context may well be
   ** unnecessary.
   **
   ** For the present demonstration we show how to update the achieved
   ** rotator mechanical angle and its velocity, though in this case we
   ** are merely fixing the RMA at 30 deg:
   */
      if ( tcsCrota ( &pk, tai, 30.0*D2R, 0.0 ) ) return -1;

   /* Perform the per-tick processing. */
      (void) tcsCtick ( &pk, tai, &dema, &demb,
                                  &demrM3, &demtM3, &demr, &dpa );

   /* Report the time stamped demands. */
      if ( i >= 0 ) {
         printf ( "\n" );
         printf (   "         timestamp: %20.10f  MJD(TAI)\n", tai );
         printf (   "    azimuth demand: %20.10f  degrees\n",
                                               slaDranrm(PI-dema)/D2R );
         printf (   "  elevation demand: %20.10f  degrees\n",
                                                             demb/D2R );
         printf (   "M3 rotation demand: %20.10f  degrees\n",
                                                slaDranrm(demrM3)/D2R );
         printf (   "    M3 tilt demand: %20.10f  degrees\n",
                                                           demtM3/D2R );
         printf (   "    rotator demand: %20.10f  degrees\n",
                                                             demr/D2R );
         printf (   "    pupil rotation: %20.10f  degrees\n",
                                                              dpa/D2R );
      }

   /* Next tick. */
   }

/*
** ----------------------------------------------
** Demonstrate the sky-to-encoders transformation
** ----------------------------------------------
*/

/* Assemble a mount virtual telescope. */
   if ( tcsCvt ( &pk, PK_M, &vt ) ) return -1;

/* Solve for encoder readings. */
   if ( tcsTVsolm ( &vt, &a1, &b1, &a2, &b2 ) ) return -1;

/* In this demo we use only the first solution. */
   a = a1;
   b = b1;

   printf ( "\nSky-to-encoders:\n" );

   switch ( pk.tel.mount ) {
   case EQUAT:
      printf ( "   HA demand    = %+23.10f  degrees\n", - a/D2R );
      printf ( "   Dec demand   = %+23.10f  degrees\n", b/D2R );
      break;
   case ALTAZ:
      printf ( "   Az demand    = %20.10f  degrees\n",
                                    slaDranrm ( PI - a )/D2R );
      printf ( "   El demand    = %20.10f  degrees\n", b/D2R );
      break;
   default:
      printf ( "   Roll demand  = %20.10f  degrees\n", a/D2R );
      printf ( "   Pitch demand = %20.10f  degrees\n", b/D2R );
   }

/*
** ----------------------------------------------
** Demonstrate the encoders-to-sky transformation
** ----------------------------------------------
*/

/* Assemble a mount virtual telescope. */
   if ( tcsCvt ( &pk, PK_M, &vt ) ) return -1;

/* Solve for target coordinates. */
   if ( tcsTVsolt ( &vt, &ra, &dec ) ) return -1;

   printf ( "\nEncoders-to-sky:\n" );
   slaDr2tf ( 4, slaDranrm(ra), &s, i4 );
   printf ( "   RA = %2.2i %2.2i %2.2i.%4.4i  ",
                                           i4[0], i4[1], i4[2], i4[3] );
   slaDr2af ( 3, dec, &s, i4 );
   printf ( "   Dec = %c%2.2i %2.2i %2.2i.%3.3i   J2000.0\n",
                                        s, i4[0], i4[1], i4[2], i4[3] );

/*
** ----------------------------
** Demonstrate dome predictions
** ----------------------------
**
** It is important to note that the simplest case is demonstrated,
** where the telescope and mount axes all intersect at one point in
** space.  In a real observatory, in particular one using a GEM or
** cross-axis equatorial mount, the 0.0 arguments would be replaced with
** actual physical offsets.  See the tcsCdome source code for the
** definitions of the various offsets.
**
*/

   if ( tcsCdome ( &pk, 5.0, 0.0, 0.0, 0.0, 0.0, 0.0, 0.0,
                   &az, &el ) ) return -1;
   printf ( "\nDome setting for science target:\n" );
   printf ( "   Azimuth      = %23.10f  degrees\n",
                                         slaDranrm(az)/D2R );
   printf ( "   Elevation    = %23.10f  degrees\n", el/D2R );

/*
** -------------------------------------------
** Demonstrate the image-to-sky transformation
** -------------------------------------------
**
** Use the pointing-origin itself, to recover the original target.
*/

/* Assemble a mount virtual telescope. */
   if ( tcsCvt ( &pk, PK_M, &vt ) ) return -1;

/* Inquire the pointing-origin number and coordinates. */
   if ( tcsCpo ( &pk, PK_PQN, 0, 0, 0.0, 0.0, &i, &x, &y ) ) return -1;

/* Insert the coordinates into the virtual telescope. */
   if ( tcsTVputp ( &vt, x, y ) ) return -1;

/* Solve for target coordinates. */
   if ( tcsTVsolt ( &vt, &ra, &dec ) ) return -1;

   printf ( "\nImage-to-sky:\n" );
   printf ( "   x  = %+12.6f mm ", x );
   printf ( "  y   = %+12.6f mm\n", y );
   slaDr2tf ( 4, slaDranrm(ra), &s, i4 );
   printf ( "   RA = %2.2i %2.2i %2.2i.%4.4i  ",
                                           i4[0], i4[1], i4[2], i4[3] );
   slaDr2af ( 3, dec, &s, i4 );
   printf ( "   Dec = %c%2.2i %2.2i %2.2i.%3.3i   J2000.0\n",
                                        s, i4[0], i4[1], i4[2], i4[3] );

/*
** ---------------------------------------------------
** Demonstrate the sky-to-image-to-sky transformations
** ---------------------------------------------------
*/

/* Assemble a mount virtual telescope. */
   if ( tcsCvt ( &pk, PK_M, &vt ) ) return -1;

/* The FK5 RA,Dec for which we want to know the x,y. */
   ra = 99.0817522314808201*D2R;
   dec = 28.3787961530615682*D2R;

/* Insert into the virtual telescope. */
   if ( tcsTVputt ( &vt, NULL, NULL, ra, dec ) ) return -1;

/* Solve for image position. */
   if ( tcsTVsolp ( &vt, &x, &y ) ) return -1;

   printf ( "\nSky-to-image:\n" );
   slaDr2tf ( 4, slaDranrm(ra), &s, i4 );
   printf ( "   RA = %2.2i %2.2i %2.2i.%4.4i  ",
                                      i4[0], i4[1], i4[2], i4[3] );
   slaDr2af ( 3, dec, &s, i4 );
   printf ( "   Dec = %c%2.2i %2.2i %2.2i.%3.3i   J2000.0\n",
                                   s, i4[0], i4[1], i4[2], i4[3] );
   printf ( "   x  = %+12.6f mm ", x );
   slaDr2af ( 3, dec, &s, i4 );
   printf ( "  y   = %+12.6f mm\n", y );

/* Insert the image position into the virtual telescope. */
   if ( tcsTVputp ( &vt, x, y ) ) return -1;

/* Solve for sky coordinates. */
   if ( tcsTVsolt ( &vt, &ra, &dec ) ) return -1;

   printf ( "\nImage-to-sky:\n" );
   printf ( "   x  = %+12.6f mm ", x );
   slaDr2af ( 3, dec, &s, i4 );
   printf ( "  y   = %+12.6f mm\n", y );
   slaDr2tf ( 4, slaDranrm(ra), &s, i4 );
   printf ( "   RA = %2.2i %2.2i %2.2i.%4.4i  ",
                                           i4[0], i4[1], i4[2], i4[3] );
   slaDr2af ( 3, dec, &s, i4 );
   printf ( "   Dec = %c%2.2i %2.2i %2.2i.%3.3i   J2000.0\n",
                                        s, i4[0], i4[1], i4[2], i4[3] );

/*
** --------------------------------------
** Demonstrate some guide star procedures
** --------------------------------------
*/

   printf ( "\nGuide star A position and predicted x,y:\n" );

/* Inquire the celestial coordinates of Guide Star A. */
   if ( tcsCtar ( &pk, PK_TQP, PK_A, 0, 0.0, 0.0, 0.0, 0.0,
                  &ast, &ra, &dec ) ) return -1;

/* Update context as a precaution (normally done by FAST processing). */
   pk.a_tara = ra;
   pk.a_tarb = dec;

   slaDr2tf ( 4, slaDranrm(ra), &s, i4 );
   printf ( "   RA = %2.2i %2.2i %2.2i.%4.4i  ",
                                           i4[0], i4[1], i4[2], i4[3] );
   slaDr2af ( 3, dec, &s, i4 );
   printf ( "   Dec = %c%2.2i %2.2i %2.2i.%3.3i   J2000.0\n",
                                        s, i4[0], i4[1], i4[2], i4[3] );

/* Assemble a guide star A virtual telescope. */
   if ( tcsCvt ( &pk, PK_A, &vt ) ) return -1;

/* Solve for image position. */
   if ( tcsTVsolp ( &vt, &x, &y ) ) return -1;

/* Report. */
   printf ( "   x  = %+12.6f mm ", x );
   slaDr2af ( 3, dec, &s, i4 );
   printf ( "  y   = %+12.6f mm\n", y );

/* Simulate some (slightly different) guider readings. */
   x = 13.9;
   y = 418.3;

/* Insert into the virtual telescope. */
   if ( tcsTVputp ( &vt, x, y ) ) return -1;

/* Solve for sky coordinates. */
   if ( tcsTVsolt ( &vt, &ra, &dec ) ) return -1;

   printf ( "\nPosition of guide star A measured by the guider:\n" );
   printf ( "   x  = %+12.6f mm ", x );
   printf ( "  y   = %+12.6f mm\n", y );

   slaDr2tf ( 4, slaDranrm(ra), &s, i4 );
   printf ( "   RA = %2.2i %2.2i %2.2i.%4.4i  ",
                                           i4[0], i4[1], i4[2], i4[3] );
   slaDr2af ( 3, dec, &s, i4 );
   printf ( "   Dec = %c%2.2i %2.2i %2.2i.%3.3i   J2000.0\n",
                                        s, i4[0], i4[1], i4[2], i4[3] );

/*
** ----------------------------------------
** Demonstrate inquiring the pointing state
** ----------------------------------------
*/

   printf ( "\nPointing state:\n" );

/* Assemble a mount virtual telescope. */
   if ( tcsCvt ( &pk, PK_M, &vt ) ) return -1;

/* Inquire mount coordinates. */
   if ( tcsTVgetm ( &vt, &a, &b ) ) return -1;

   printf ( "   Roll   = %19.10f  degrees\n", a/D2R );
   printf ( "   Pitch  = %19.10f  degrees\n", b/D2R );

/* Inquire pointing-origin coordinates. */
   if ( tcsTVgetp ( &vt, &x, &y ) ) return -1;

   printf ( "   x      = %+19.10f  mm\n", x );
   printf ( "   y      = %+19.10f  mm\n", y );

/* Inquire sky coordinates, mount system. */
   if ( tcsTVgett ( &vt, NULL, NULL, &ra, &dec ) ) return -1;

   printf ( "   RA     = %19.10f  degrees\n", ra/D2R );
   printf ( "   Dec    = %19.10f  degrees\n", dec/D2R );

/* Inquire sky coordinates, user system. */
   if ( tcsTVgett ( &vt, &pk.u_ast, &pk.tsite, &az, &el ) ) return -1;

   printf ( "   Az     = %19.10f  degrees\n", az/D2R );
   printf ( "   El     = %19.10f  degrees\n", el/D2R );

/* Inquire position angle of rotator y-axis. */
   if ( tcsTVypa ( &vt, &ypau ) ) return -1;

   printf ( "   +y PA  = %19.10f  degrees\n", ypau/D2R );

/*
** ---------------------------------------------------------
** Demonstrate rotator orientation wrt different sky systems
** ---------------------------------------------------------
*/

   tcsCypa ( &pk, &ypam, &ypar, &ypau );

   printf ( "\nPosition angle of pointing-origin +y direction:\n" );
   printf ( "%13.6f degrees (science system)\n", slaDranrm(ypam)/D2R );
   printf ( "%13.6f degrees (rotator-control system)\n",
                                                  slaDranrm(ypar)/D2R );
   printf ( "%13.6f degrees (user system)\n", slaDranrm(ypau)/D2R );

/*
** --------------------------------------------
** Demonstrate offsetting in "user" coordinates
** --------------------------------------------
**
** The auxiliary, or "user", astrometric system has been set to observed
** az/el.  By working via these coordinates we will offset the telescope
** to a position that was 1 arcminute to the right of the science target
** at the time of the last tick.
*/

/* Assemble a auxiliary virtual telescope. */
   if ( tcsCvt ( &pk, PK_U, &vt ) ) return -1;

/* Change the pointing-origin to be the rotator axis. */
   if ( tcsTVputp ( &vt, 0.0, 0.0 ) ) return -1;

/* Report the corresponding user coordinates. */
   if ( tcsTVsolt ( &vt, &az, &el ) ) return -1;

   printf ( "\nAzimuth and elevation of rotator axis:\n" );
   printf ( "   Azimuth      = %23.10f  degrees\n",
                                         slaDranrm(az)/D2R );
   printf ( "   Elevation    = %23.10f  degrees\n", el/D2R );

/* Start again but leave the pointing-origin where it is. */
   if ( tcsCvt ( &pk, PK_U, &vt ) ) return -1;

   if ( tcsTVsolt ( &vt, &az, &el ) ) return -1;

   printf ( "\nAzimuth and elevation of pointing-origin:\n" );
   printf ( "   Azimuth      = %23.10f  degrees\n",
                                         slaDranrm(az)/D2R );
   printf ( "   Elevation    = %23.10f  degrees\n", el/D2R );

/* Choose a position 1 arcmin to the right. */
   az += atan2 ( 60.0*AS2R, cos(el) );

   printf ( "\n60 arcseconds to the right:\n" );
   printf ( "   Azimuth      = %23.10f  degrees\n",
                                         slaDranrm(az)/D2R );
   printf ( "   Elevation    = %23.10f  degrees\n", el/D2R );

/* Insert the changed target into the (auxiliary) virtual telescope. */
   if ( tcsTVputt ( &vt, NULL, NULL, az, el) ) return -1;

/* Solve for encoder demands. */
   if ( tcsTVsolm ( &vt, &a1, &b1, &a2, &b2 ) ) return -1;

/* Assemble a mount virtual telescope. */
   if ( tcsCvt ( &pk, PK_M, &vt ) ) return -1;

/* Insert the encoder demands for the horizontally offset target. */
   if ( ! jbp ) {
      if ( tcsTVputm ( &vt, a1, b1 ) ) return -1;
   } else {
      if ( tcsTVputm ( &vt, a2, b2 ) ) return -1;
   }

/* Solve for RA,Dec. */
   if ( tcsTVsolt ( &vt, &ra, &dec ) ) return -1;

   printf ( "\nThe corresponding offset target:\n" );
   slaDr2tf ( 4, slaDranrm(ra), &s, i4 );
   printf ( "   RA = %2.2i %2.2i %2.2i.%4.4i  ",
                                           i4[0], i4[1], i4[2], i4[3] );
   slaDr2af ( 3, dec, &s, i4 );
   printf ( "   Dec = %c%2.2i %2.2i %2.2i.%3.3i   J2000.0\n",
                                        s, i4[0], i4[1], i4[2], i4[3] );

/*
** -----------------------
** NFIRAOS ADC orientation
** -----------------------
*/

   x = sin(dpa);
   y = cos(dpa);
   printf ( "\nFCRS_NFIRAOS "
            "unit vector towards IR end of dispersed image:\n"
            "   [ %+12.9f, %+12.9f ]\n", x, y );

/*
** --------------------
** IRIS ADC orientation
** --------------------
*/

   x = sin(dpa+demr);
   y = cos(dpa+demr);
   printf ( "\nFCRS_IRIS-ROT "
            "unit vector towards IR end of dispersed image:\n"
            "   [ %+12.9f, %+12.9f ]\n", x, y );

/*
** ----------------------
** LGSF K-mirror rotation
** ----------------------
*/
   printf ( "\nLGSF K-mirror demand angle:\n"
            "   %12.9f or %12.9f\n", slaDranrm(dpa/2.0)/D2R,
                                     slaDranrm((dpa+PI2)/2.0)/D2R);

/*
** --------------------------
** NFIRAOS NGS image position
** --------------------------
*/

/* Assemble a mount virtual telescope. */
   if ( tcsCvt ( &pk, PK_M, &vt ) ) return -1;

/* Select axis as pointing origin. */
   if ( tcsTVputp ( &vt, 0.0, 0.0 ) ) return -1;

/* Solve for sky coordinates. */
   if ( tcsTVsolt ( &vt, &ra, &dec ) ) return -1;

/* Put a fictitious NFIRAOS natural guide star nearby. */
   pk.n_ast = pk.m_ast;
   rg = ra + 0.01*D2R;
   dg = dec + 0.02*D2R;
   if ( tcsCtar ( &pk, PK_TBA, PK_N, 0, 0.0, tai, rg, dg,
                  &ast, &w1, &w2  ) ) return -1;

/* Preempt the FAST processing. */
   pk.n_tara = w1;
   pk.n_tarb = w2;

/* Assemble a NFIRAOS NGS virtual telescope. */
   if ( tcsCvt ( &pk, PK_N, &vt ) ) return -1;

/* Solve for image position and transform from TCSpk to FRCS_174.5. */
   if ( tcsTVsolp ( &vt, &x, &y ) ) return -1;
   x = -x;

/* Report. */
   printf ( "\nNFIRAOS natural guide star:\n" );
   slaDr2tf ( 4, slaDranrm(rg), &s, i4 );
   printf ( "   RA = %2.2i %2.2i %2.2i.%4.4i  ",
                                           i4[0], i4[1], i4[2], i4[3] );
   slaDr2af ( 3, dg, &s, i4 );
   printf ( "   Dec = %c%2.2i %2.2i %2.2i.%3.3i  J2000.0\n",
                                        s, i4[0], i4[1], i4[2], i4[3] );
   slaDr2tf ( 4, slaDrange ( w1 - ra ), &s, i4 );
   printf ( "   FCRS_174.5 xy (mm) = " );
   printf ( " %+11.6f", x );
   printf ( " %+11.6f\n", y );

/*
** -----------------------
** Pupil rotation for IRIS
** -----------------------
**
** To first order this is the same as for the orientation of the IRIS
** ADCs.  More rigorously, a pointing model that omits the azimuth axis
** tilt terms AN and AW would be needed.
*/

   x = sin(dpa+demr);
   y = cos(dpa+demr);
   printf ( "\nImage of the M1CRS axes in FCRS_IRIS-ROT:\n"
            "   x-axis [ %+12.9f, %+12.9f ]\n"
            "   y-axis [ %+12.9f, %+12.9f ]\n", x, y, -y, x );

/*
** ----------------------------------------
** Pupil rotation for AO Executive software
** ----------------------------------------
**
** To first order this is the same as for the orientation of the NFIRAOS
** NGSF ADC.  More rigorously, a pointing model that omits the azimuth
** axis tilt terms AN and AW would be needed.
*/

   x = sin(dpa);
   y = cos(dpa);
   printf ( "\nImage of the M1CRS axes in FCRS_174.5:\n"
            "   x-axis [ %+12.9f, %+12.9f ]\n"
            "   y-axis [ %+12.9f, %+12.9f ]\n", x, y, -y, x );

/*
** -----------------------------
** Image shifts from NFIRAOS ADC
** -----------------------------
**
** For the sake of this demonstration it is assumed that no corrections
** are being applied for image shifts from ADCs associated with the
** instrument (e.g. IRIS).
*/

/* ADC shifts (fictitious), FCRS_174.5, mm. */
   xmm = 3.1;
   ymm = 5.2;

/* Transform into TCSpk [dx,dy]. */
   x = -xmm / pk.tel.fl;
   y = ymm / pk.tel.fl;

/* Report. */
   printf ( "\nImage shifts from NFIRAOS ADC:\n"
            "   FCRS_174.5  [ %+12.6f mm, %+12.6f mm ]\n"
            "   TCSpk       [ %+15.9f, %+15.9f ]\n",
            xmm, ymm, x, y );

/*
** --------------------------
** Image shifts from IRIS ADC
** --------------------------
*/

/* ADC shifts (fictitious), FCRS_IRIS-ROT, mm. */
   xmm = 1.5;
   ymm = 2.5;

/* Transform into TCSpk [dx,dy]. */
   x = -xmm / pk.tel.fl;
   y = ymm / pk.tel.fl;

/* Report. */
   printf ( "\nImage shifts from IRIS ADC:\n"
            "   FCRS_IRIS-ROT  [ %+12.6f mm, %+12.6f mm ]\n"
            "   TCSpk          [ %+15.9f, %+15.9f ]\n",
            xmm, ymm, x, y );

/*
** ----------------------------------------------
** OIWFS (and equivalently ODGW) position demands
** ----------------------------------------------
*/

/* Begin the report. */
   printf ( "\nOIWFS/ODGW guide star:\n" );

/* Use guide star from before as instrument guider A target. */
   pk.a_ast = pk.m_ast;
   if ( tcsCtar ( &pk, PK_TBA, PK_A, 0, 0.0, tai, rg, dg,
                  &ast, &w1, &w2  ) ) return -1;

/* Update context as a precaution (normally done by FAST processing). */
   pk.a_tara = w1;
   pk.a_tarb = w2;

/* Assemble an instrument guider A virtual telescope. */
   if ( tcsCvt ( &pk, PK_A, &vt ) ) return -1;

/* Solve for image position (mm) and express in FCRS_IRIS-ROT. */
   if ( tcsTVsolp ( &vt, &x, &y ) ) return -1;
   xmm = - x;
   ymm = y;

/* TCSpk values to radians. */
   x /= pk.tel.fl;
   y /= pk.tel.fl;

/* Report. */
   printf ( "   Predicted x,y:\n"
            "        TCSpk          [ %+15.9f, %+15.9f ]\n"
            "        FCRS_IRIS-ROT  [ %+12.6f mm, %+12.6f mm ]\n",
            x, y, xmm, ymm );

/* Simulate some (slightly different) image measurements. */
   xmmm = xmm + 1.5;
   ymmm = ymm + 0.9;
   xm = - xmmm / pk.tel.fl;
   ym = ymmm / pk.tel.fl;

/* Report. */
   printf ( "\n   Measured x,y:\n"
            "      TCSpk          [ %+15.9f, %+15.9f ]\n"
            "      FCRS_IRIS-ROT  [ %+12.6f mm, %+12.6f mm ]\n",
            xm, ym, xmmm, ymmm );

/* Rotate the difference into alignment with the nominal mount. */
   dx = x - xm;
   dy = y - ym;
   tcsXy2xe ( dx, dy, &pk.tel.rotl, demr, a, b, &vt.ga, &vt.gb );

/* Report. */
   printf ( "\n   Guiding correction:\n"
            "                  [ %+9.12f, %+9.12f ]\n",
            vt.ga, vt.gb );

/*
** ---------------------------------------------------------
** Guiding corrections from pupil motion caused by M3 errors
** ---------------------------------------------------------
**
** The function of M3 is to place the pupil on the FCRS z-axis.  If in
** FCRS there is an angular displacement of the pupil, this is evidence
** that M3 is not in the correct attitude.  This will have pointing
** consequences, requiring guiding corrections.  The angular
** displacement of the pupil gives rise to a pointing error that is
** smaller by the factor 20/450, i.e. the ratio between back focus
** (distance from M3 to focus) to fical length.
*/

/* Example pupil offsets (FCRS, radians). */
   dx = 0.02*D2R;
   dy = 0.03*D2R;

/* Required TCSpk guiding corrections. */
   r = 20.0/450.0;
   ga = r * ( dx*cos(dpa) - dy*sin(dpa) );
   gb = r * ( - dx*sin(dpa) - dy*cos(dpa) );

/* Report. */
   printf ( "\nGuiding corrections due to M3 steering error:\n"
            "   Angular displacement of pupil seen in M3:\n"
            "      [ %+9.6f, %+9.6f ]  FCRS, degrees\n"
            "   Guiding corrections:\n"
            "      ga = %+13.9f  gb = %+13.9f  radians\n",
            dx/D2R, dy/D2R, ga, gb );

/*
** The TCS demonstration terminates here;  an operational system would
** of course execute continuously, reacting to (i) user-interface
** events and (ii) successive clock ticks.
*/
   return 0;
}

/*--------------------------------------------------------------------*/

double Wait ()

/*
**  - - - - -
**   W a i t
**  - - - - -
**
**  Each time this function is called it returns a TAI MJD that is one
**  tick later than from the previous call.
**
**  Defined in tcsmac.h
**     DAYSEC    double       day length in seconds
**
**  Called:   tcsTime
**
**  Note:
**
**     The usual TCSpk dummy clock function tcsClock is called at the
**     start, and an arbitrary offset is introduced merely to facilitate
**     comparisons with the other demonstration programs (in particular
**     tcs_demo).  A real TCS application would have no such
**     complications:  instead, there would be functions that read a
**     real clock together with capabilities to trigger processing at
**     the adopted tick rate.
**
**  Last revision:   4 September 2014
**
**  Copyright P.T.Wallace.  All rights reserved.
*/

{
/* Tick duration (TAI days) */
   const double tick = 0.05 / DAYSEC;

/* Ticks since starting time for next result */
   static int itick = 0;

/* Base TAI (MJD) */
   static double t0;

/* Working TAI (MJD) */
   double tai;


/* Get the current TAI. */
   if ( ! itick ) {

   /* First time only:  call the TCSpk dummy time service. */
      (void) tcsTime ( &tai );

   /* Go 21 ticks earlier (see note)... */
      tai -= ( (double) 21 ) * tick;

   /* ...and make it the base. */
      t0 = tai;

   } else {

   /* Subsequently (i.e. normal case) get the time for this tick. */
      tai = t0 + ( (double) itick ) * tick;
   }

/* Prepare for the next tick. */
   itick++;

/* Return the current TAI MJD. */
   return tai;
}

/*--------------------------------------------------------------------*/

#include "interface.h"

#include <stdio.h>
#include <time.h>
#include "tpk/UnixClock.h"

using std::cin;
using std::cout;
using std::endl;
using std::string;

namespace example {

#define AS2R tpk::TcsLib::as2r

// The SlowScan class implements the "slow" loop of the application.

class SlowScan: public ScanTask {
private:
	tpk::TimeKeeper& time;
	tpk::Site& site;
	void scan() {

		// Update the Site object with the current time. If we had a weather
		// server we would also update the atmospheric conditions here.
		site.refresh(time.tai());
	}
public:
	SlowScan(tpk::TimeKeeper& t, tpk::Site& s) :
		ScanTask(6000, 3), time(t), site(s) {
	}
	;
};

// The MediumScan class implements the "medium" loop.

class MediumScan: public ScanTask {
private:
	tpk::TmtMountVt mountVt;
	tpk::MountVt enclosureVt;
	tpk::RotatorVt rotatorVt;
	tpk::AutoGuider oiwfs1;

	void scan() {
		// Update the pointing model.
		mountVt.updatePM();

		// Update the mount SPMs,
		mountVt.update();

		// Update the pointing model.
		enclosureVt.updatePM();

		// Update the enclosure SPMs,
		enclosureVt.update();

		// Update the rotator SPMs,
		rotatorVt.update();
		// Update the pointing model.
		rotatorVt.updatePM();

		// Update the AutoGuider SPMs,
		oiwfs1.update();

	}
public:
	MediumScan(tpk::TmtMountVt& m, tpk::MountVt& e, tpk::RotatorVt& r, tpk::AutoGuider& g1) :
		ScanTask(500, 2), mountVt(m), enclosureVt(e), rotatorVt(r), oiwfs1(g1) {
	}
	;
};

// The FastScan class implements the "fast" loop.
class FastScan: public ScanTask {
private:
	TpkPoc* poc;
	tpk::TimeKeeper& time;
	tpk::TmtMountVt& mountVt;
	tpk::MountVt& enclosureVt;
	tpk::RotatorVt rotatorVt;
	tpk::AutoGuider oiwfs1;

	void scan() {

		// Update the time
		time.update();

		// Compute the mount and rotator position demands.
		mountVt.track(1);

		// Compute the enclosure  position demands.
		enclosureVt.track(1);

		// Compute the rotatorVt  position demands.
		rotatorVt.track(1);

		// Compute the AutoGuider  position demands.
		oiwfs1.track(1, 0.0);

		// Get the mount az, el and M3 demands in degrees.

		double mountAz = 180.0 - (mountVt.roll() / tpk::TcsLib::d2r);
		double mountEl = mountVt.pitch() / tpk::TcsLib::d2r;

		double m3R = mountVt.m3Azimuth() / tpk::TcsLib::d2r;
		double m3T = 90 - (mountVt.m3Elevation() / tpk::TcsLib::d2r);

		// Get the enclosure az, el demands in degrees.
		double encAz = 180.0 - (enclosureVt.roll() / tpk::TcsLib::d2r);
		double encEl = enclosureVt.pitch() / tpk::TcsLib::d2r;

		// Get RMA demands in degrees.
		double rma = rotatorVt.rma();

		double x1 = oiwfs1.x();
		double y1 = oiwfs1.y();

		if (poc)
			poc->newDemands(mountAz, mountEl, m3R, m3T, encAz, encEl, rma, x1, y1);

	}
public:
	FastScan(TpkPoc* pk, tpk::TimeKeeper& t, tpk::TmtMountVt& m,
			tpk::MountVt& e, tpk::RotatorVt& r, tpk::AutoGuider& g1) :
		ScanTask(10, 1), poc(pk), time(t), mountVt(m), enclosureVt(e), rotatorVt(r),
				oiwfs1(g1) {

	}
	;
};

void TpkPoc::newDemands(double mountAz, double mountEl, double m3R, double m3T,
		double encAz, double encEl, double rma, double x1, double y1) {

	if (demandsNotifier != 0) {
		demandsNotifier->newDemands(mountAz, mountEl, m3R, m3T, encAz, encEl, rma, x1, y1);
	}
}

void TpkPoc::init() {

	// Set up standard out so that it formats double with the maximum precision.
	cout.precision(14);
	cout.setf(std::ios::showpoint);

	// Construct the TCS. First we need a clock...
	tpk::UnixClock clock(37.0 // Assume that the system clock is st to UTC.
			// TAI-UTC is 37 sec at the time of writing
			);

	// and a Site...
	site = new tpk::Site(clock.read(), 0.56, // UT1-UTC (seconds)
			37.0, // TAI-UTC (seconds)
			32.184, // TT-TAI (seconds)
			-155.4775033, // East longitude (for Hawaii)
			19.82900194, // Latitude (for Hawaii)
			4160, // Height (metres) (for Hawaii)
			0.1611, 0.4475 // Polar motions (arcsec)
			);

	// and a "time keeper"...
	tpk::TimeKeeper time(clock, *site);

	// Create a transformation that converts mm to radians for a 450000.0mm	 focal length.
	tpk::AffineTransform transf(0.0, 0.0, 1.0 / 450000.0, 0.0);

	// Create mount and enclosure virtual telescopes.
	// M3 comes automatically with TmtMountVt

	mountVt = new tpk::TmtMountVt(time, *site, tpk::BentNasmyth(174.5, 0.0),
			&transf, 0, tpk::ICRefSys());

	enclosureVt = new tpk::MountVt(time, *site, tpk::BentNasmyth(174.5, 0.0),
			tpk::AltAzMountType(), &transf, 0, tpk::ICRefSys());

	tpk::PointingModel model;
	rotatorVt = new tpk::RotatorVt(*mountVt, tpk::BentNasmyth(174.5, 0.0), 0,
				transf, &model);

	tpk::AffineTransform gtransf(0.0, 0.0, 1.0 / 450000.0, 0.0);

	//	tpk::PiFilter rollFilter(0.5);
	//	tpk::PiFilter pitchFilter(0.5);
	//	tpk::PointingControl pc(*mountVt, rollFilter, pitchFilter);
	//	tpk::AvgFilter2D filter2d(0);
	//	oiwfs1 = new tpk::AutoGuider(*mountVt, pc, gtransf, filter2d);
	oiwfs1 = new tpk::AutoGuider(
			*mountVt,
			*(new tpk::PointingControl(*mountVt, *(new tpk::Filter),
					*(new tpk::Filter))), gtransf, tpk::Filter2D());

	// Create and install a pointing model. In a real system this would be initialised from a file.
//	tpk::PointingModel model;
	mountVt->newPointingModel(model);
	enclosureVt->newPointingModel(model);
	rotatorVt->newPointingModel(model);

	// Make ourselves a real-time process if we have the privilege.
	ScanTask::makeRealTime();

	// Create the slow, medium and fast threads.
	SlowScan slow(time, *site);
	MediumScan medium(*mountVt, *enclosureVt, *rotatorVt, *oiwfs1);

	FastScan fast(this, time, *mountVt, *enclosureVt, *rotatorVt, *oiwfs1);

	// Start the scheduler thread.
	ScanTask::startScheduler();

	// Set the field orientation.
	mountVt->setPai(0.0, tpk::ICRefSys());
	enclosureVt->setPai(0.0, tpk::ICRefSys());
	rotatorVt->setPai(0.0, tpk::ICRefSys());

	// set pointing origin
	tpk::ImagePosManager po(10, 20, 100);
	mountVt->newPointingOrigin(po);

	tpk::ICRSTarget target(*site, "06 36 18.6205 28 21 43.666");

	tpk::ICRSTarget probeTarget1(*site, "06 36 06.5173 28 26 14.071");
	//
	// Set the mount and enclosure to the same target
	//
	mountVt->newTarget(target);
	enclosureVt->newTarget(target);
	rotatorVt->newTarget(target);

	oiwfs1->newTarget(probeTarget1);

//	mountVt->setWavelength(1.0);
//	oiwfs1->setWavelength(1.0);

	for (;;) {
		nanosleep((const struct timespec[]) { {0, 500000L}}, NULL);
	}
}

void TpkPoc::_register(IDemandsCB* demandsNotify) {
	demandsNotifier = demandsNotify;
	if (demandsNotifier == 0)
		printf("Register failed\n");
}

void TpkPoc::newTarget(double ra, double dec) {
	tpk::ICRSTarget target(*site, (ra * tpk::TcsLib::d2r),
			(dec * tpk::TcsLib::d2r));
	//	tpk::ICRSTarget target1(*site, "12 23 11 06 45 12");
	//
	// Set the mount and enclosure to the same target
	//
	mountVt->newTarget(target);
	enclosureVt->newTarget(target);
	rotatorVt->newTarget(target);
	oiwfs1->newTarget(target);
}

void TpkPoc::offset(double raO, double decO) {
	// Send an offset to the mount

	tpk::Offset* offset = new tpk::Offset(
			tpk::xycoord(raO * AS2R, decO * AS2R), tpk::ICRefSys());
	mountVt->setOffset(*offset);
}

}

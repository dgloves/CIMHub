package gov.pnnl.gridappsd.cimhub.components;
//	----------------------------------------------------------
//	Copyright (c) 2017, Battelle Memorial Institute
//	All rights reserved.
//	----------------------------------------------------------

import org.apache.jena.query.*;
import java.util.HashMap;
import org.apache.commons.math3.complex.Complex;

public class DistXfmrCodeRating extends DistComponent {
	public String pname;
	public String tname;
	public String id;
	public String[] eid;
	public String[] ename;
	public int[] wdg;
	public String[] conn;
	public int[] ang;
	public double[] ratedS; 
	public double[] ratedU;
	public double[] r;
	public int size;

	public boolean glmUsed;
  public boolean glmAUsed;
  public boolean glmBUsed;
  public boolean glmCUsed;

	public String GetJSONEntry () {
		StringBuilder buf = new StringBuilder ();

		buf.append ("{\"name\":\"" + pname +"\"");
		buf.append (",\"mRID\":\"" + id +"\"");
		buf.append ("}");
		return buf.toString();
	}

	private void SetSize (int val) {
		size = val;
		eid = new String[size];
		ename = new String[size];
		wdg = new int[size];
		conn = new String[size];
		ang = new int[size];
		ratedS = new double[size];
		ratedU = new double[size];
		r = new double[size];
	}

	public DistXfmrCodeRating (ResultSet results, HashMap<String,Integer> map) {
		if (results.hasNext()) {
			QuerySolution soln = results.next();
			String p = soln.get("?pname").toString();
			String t = soln.get("?tname").toString();
			pname = SafeName (p);
			tname = SafeName (t);
			id = soln.get("?id").toString();
			SetSize (map.get(tname));
			for (int i = 0; i < size; i++) {
				eid[i] = soln.get("?eid").toString();
				ename[i] = SafeName (soln.get("?ename").toString());
				wdg[i] = Integer.parseInt (soln.get("?enum").toString());
				conn[i] = soln.get("?conn").toString();
				ang[i] = Integer.parseInt (soln.get("?ang").toString());
				ratedS[i] = Double.parseDouble (soln.get("?ratedS").toString());
				ratedU[i] = Double.parseDouble (soln.get("?ratedU").toString());
				r[i] = Double.parseDouble (soln.get("?res").toString());
				if ((i + 1) < size) {
					soln = results.next();
				}
			}
		}		
	}

	public String DisplayString() {
		StringBuilder buf = new StringBuilder ("");
		buf.append (pname + ":" + tname);
		for (int i = 0; i < size; i++) {
			buf.append ("\n  wdg=" + Integer.toString(wdg[i]) + " conn=" + conn[i] + " ang=" + Integer.toString(ang[i]));
			buf.append (" U=" + df4.format(ratedU[i]) + " S=" + df4.format(ratedS[i]) + " r=" + df4.format(r[i]));
		}
		return buf.toString();
	}

  private void AppendGldPhaseRatings (StringBuilder buf, String sKVA) {
    if (glmAUsed) {
      buf.append ("  powerA_rating " + sKVA + ";\n");
      buf.append ("  powerB_rating 0.0;\n");
      buf.append ("  powerC_rating 0.0;\n");
    } else if (glmBUsed) {
      buf.append ("  powerA_rating 0.0;\n");
      buf.append ("  powerB_rating " + sKVA + ";\n");
      buf.append ("  powerC_rating 0.0;\n");
    } else if (glmCUsed) {
      buf.append ("  powerA_rating 0.0;\n");
      buf.append ("  powerB_rating 0.0;\n");
      buf.append ("  powerC_rating " + sKVA + ";\n");
    }
  }

  public void AddGldPrimaryPhase (String phs) {
    if (phs.contains("A")) glmAUsed = true;
    if (phs.contains("B")) glmBUsed = true;
    if (phs.contains("C")) glmCUsed = true;
  }

	public String GetGLM (DistXfmrCodeSCTest sct, DistXfmrCodeOCTest oct) {
		StringBuilder buf = new StringBuilder("object transformer_configuration {\n");

		double rpu = 0.0;
		double zpu = 0.0;
		double zbase1 = ratedU[0] * ratedU[0] / ratedS[0];
		double zbase2 = ratedU[1] * ratedU[1] / ratedS[1];
		if ((sct.ll[0] > 0.0) && (size < 3)) {
			rpu = 1000.0 * sct.ll[0] / ratedS[0];
		} else {
			// hard-wired for SINGLE_PHASE_CENTER_TAPPED,
			// which is the only three-winding case that GridLAB-D supports
			rpu = (r[0] / zbase1) + 0.5 * (r[1] / zbase2);
		}
		if (rpu <= 0.000001) {
			rpu = 0.000001; // GridLAB-D doesn't like zero
		}
		if (sct.fwdg[0] == 1) {
			zpu = sct.z[0] / zbase1;
		} else if (sct.fwdg[0] == 2) {
			zpu = sct.z[0] / zbase2;
		}
		double xpu = zpu;
//		if (zpu >= rpu) {
//			xpu = Math.sqrt (zpu * zpu - rpu * rpu);
//		}

		String sConnect = GetGldTransformerConnection (conn, size);
		String sKVA = df3.format (ratedS[0] * 0.001);
		buf.append ("  name \"xcon_" + tname + "\";\n");
		buf.append ("  power_rating " + sKVA + ";\n");
		if (sConnect.equals("SINGLE_PHASE_CENTER_TAPPED")) {
      AppendGldPhaseRatings (buf, sKVA);
			buf.append ("  primary_voltage " + df3.format (ratedU[0]) + ";\n");
			buf.append ("  secondary_voltage " + df3.format (ratedU[1]) + ";\n");
		} else if (sConnect.equals("SINGLE_PHASE")) {
      AppendGldPhaseRatings (buf, sKVA);
			buf.append ("  primary_voltage " + df3.format (ratedU[0]) + ";\n");  // do not use Vll for single-phase wye impedance base
			buf.append ("  secondary_voltage " + df3.format (ratedU[1]) + ";\n");
			sConnect = "WYE_WYE";
		} else {
			buf.append ("  primary_voltage " + df3.format (ratedU[0]) + ";\n");
			buf.append ("  secondary_voltage " + df3.format (ratedU[1]) + ";\n");
		}
		if (sConnect.equals ("Y_D")) {
			buf.append("  connect_type WYE_WYE; // should be Y_D\n");
		} else {
			buf.append("  connect_type " + sConnect + ";\n");
		}
		if (sConnect.equals ("SINGLE_PHASE_CENTER_TAPPED")) {
      // the hard-wired interlace assumptions use 0.8*zpu, 0.4*zpu, 0.4*zpu for X values,
      // which would match X12 and X13, but not X23 from the original short-circuit test data
      double r1 = 0.5 * rpu;
      double r2 = rpu;
      double r3 = rpu;
      double x1 = 0.8 * zpu;
      double x2 = 0.4 * zpu;
      double x3 = x2;
      if (size == 3) { // use the OpenDSS approach, should match Z23, should also work for non-interlaced
        double x12 = 0.0, x13 = 0.0, x23 = 0.0;
        double zbase = ratedU[0] * ratedU[0] / ratedS[0];
        r1 = r[0] / zbase;
        zbase = ratedU[1] * ratedU[1] / ratedS[1];
        r2 = r[1] / zbase;
        zbase = ratedU[2] * ratedU[2] / ratedS[2];
        r3 = r[2] / zbase;
        for (int i = 0; i < sct.size; i++) {
          int fwdg = sct.fwdg[i];
          int twdg = sct.twdg[i];
          zbase = ratedU[fwdg-1] * ratedU[fwdg-1] / ratedS[fwdg-1];
          if ((fwdg == 1 && twdg == 2) || (fwdg == 2 && twdg == 1)) {
            x12 = sct.z[i] / zbase;
          } else if ((fwdg == 1 && twdg == 3) || (fwdg == 3 && twdg == 1)) {
            x13 = sct.z[i] / zbase;
          } else if ((fwdg == 2 && twdg == 3) || (fwdg == 3 && twdg == 2)) {
            x23 = sct.z[i] / zbase;
          }
        }
        x1 = 0.5 * (x12 + x13 - x23);
        x2 = 0.5 * (x12 + x23 - x13);
        x3 = 0.5 * (x13 + x23 - x12);
      }
      String impedance = CFormat (new Complex (r1, x1));
      String impedance1 = CFormat (new Complex (r2, x2));
      String impedance2 = CFormat (new Complex (r3, x3));
			buf.append ("  impedance " + impedance + ";\n");
			buf.append ("  impedance1 " + impedance1 + ";\n");
			buf.append ("  impedance2 " + impedance2 + ";\n");
		} else {
			buf.append ("  resistance " + df6.format (rpu) + ";\n");
			buf.append ("  reactance " + df6.format (xpu) + ";\n");
		}
    // as of v4.3, GridLAB-D implementing shunt_impedance for only two connection types
    if (sConnect.equals ("SINGLE_PHASE_CENTER_TAPPED") || sConnect.equals ("WYE_WYE")) {
      if (oct.iexc > 0.0) {
        buf.append ("  shunt_reactance " + df6.format (100.0 / oct.iexc) + ";\n");
      }
      if (oct.nll > 0.0) {
        buf.append ("  shunt_resistance " + df6.format (ratedS[0] / oct.nll / 1000.0) + ";\n");
      }
    }
		buf.append("}\n");
		return buf.toString();
	}

	public String GetDSS(DistXfmrCodeSCTest sct, DistXfmrCodeOCTest oct) {
		boolean bDelta;
		int phases = 3;
		double zbase, xpct;
		int fwdg, twdg, i;

		for (i = 0; i < size; i++) {
			if (conn[i].contains("I")) {
				phases = 1;
			}
		}
		StringBuilder buf = new StringBuilder("new Xfmrcode." + tname + " windings=" + Integer.toString(size) +
																					" phases=" + Integer.toString(phases));

		// short circuit tests - valid only up to 3 windings
		for (i = 0; i < sct.size; i++) {
			fwdg = sct.fwdg[i];
			twdg = sct.twdg[i];
			zbase = ratedU[fwdg-1] * ratedU[fwdg-1] / ratedS[fwdg-1];
			xpct = 100.0 * sct.z[i] / zbase; // not accounting for ll
			if ((fwdg == 1 && twdg == 2) || (fwdg == 2 && twdg == 1)) {
				buf.append(" xhl=" + df6.format(xpct));
			} else if ((fwdg == 1 && twdg == 3) || (fwdg == 3 && twdg == 1)) {
				buf.append(" xht=" + df6.format(xpct));
			} else if ((fwdg == 2 && twdg == 3) || (fwdg == 3 && twdg == 2)) {
				buf.append(" xlt=" + df6.format(xpct));
			}
		}
		// open circuit test
		buf.append (" %imag=" + df3.format(oct.iexc) + " %noloadloss=" + df3.format(100.0 * 1000.0 * oct.nll / ratedS[0]) + "\n");

		// winding ratings
		for (i = 0; i < size; i++) {
			if (conn[i].contains("D")) {
				bDelta = true;
			} else {
				bDelta = false;
			}
			zbase = ratedU[i] * ratedU[i] / ratedS[i];
			buf.append("~ wdg=" + Integer.toString(i + 1) + " conn=" + DSSConn(bDelta) +
								 " kv=" + df3.format(0.001 * ratedU[i]) + " kva=" + df1.format(0.001 * ratedS[i]) +
								 " %r=" + df6.format(100.0 * r[i] / zbase) + "\n");
		}
		return buf.toString();
	}

  public static String szCSVHeader = "Name,NumWindings,NumPhases,Wdg1kV,Wdg1kVA,Wdg1Conn,Wdg1R,Wdg2kV,Wdg2kVA,Wdg2Conn,Wdg2R,Wdg3kV,Wdg3kVA,Wdg3Conn,Wdg3R,%x12,%x13,%x23,%imag,%NoLoadLoss";

  public String GetCSV (DistXfmrCodeSCTest sct, DistXfmrCodeOCTest oct) {
    boolean bDelta;
    int phases = 3;
    double zbase, xpct;
    int fwdg, twdg, i;

    for (i = 0; i < size; i++) {
      if (conn[i].contains("I")) {
        phases = 1;
      }
    }
    StringBuilder buf = new StringBuilder(tname + "," + Integer.toString(size) + "," + Integer.toString(phases));

    // winding ratings: kV, kVA, Conn, R
    for (i = 0; i < size; i++) {
      if (conn[i].contains("D")) {
        bDelta = true;
      } else {
        bDelta = false;
      }
      zbase = ratedU[i] * ratedU[i] / ratedS[i];
      buf.append ("," + df3.format(0.001 * ratedU[i]) + "," + df1.format(0.001 * ratedS[i]) + "," + DSSConn(bDelta) +
                 "," + df6.format(100.0 * r[i] / zbase));
    }
    if (size < 3) buf.append (",,,");

    // short circuit tests - valid only up to 3 windings
    double x12 = 0.0, x13 = 0.0, x23 = 0.0;
    for (i = 0; i < sct.size; i++) {
      fwdg = sct.fwdg[i];
      twdg = sct.twdg[i];
      zbase = ratedU[fwdg-1] * ratedU[fwdg-1] / ratedS[fwdg-1];
      xpct = 100.0 * sct.z[i] / zbase; // not accounting for ll
      if ((fwdg == 1 && twdg == 2) || (fwdg == 2 && twdg == 1)) {
        x12 = xpct;
      } else if ((fwdg == 1 && twdg == 3) || (fwdg == 3 && twdg == 1)) {
        x13 = xpct;
      } else if ((fwdg == 2 && twdg == 3) || (fwdg == 3 && twdg == 2)) {
        x23 = xpct;
      }
    }
    buf.append ("," + df6.format(x12) + "," + df6.format(x13) + "," + df6.format(x23));

    // open circuit test
    buf.append ("," + df3.format(oct.iexc) + "," + df3.format(0.001 * oct.nll / ratedS[0]) + "\n");
    return buf.toString();
  }

  public String GetKey() {
		return tname;
	}
}


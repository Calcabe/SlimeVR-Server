package dev.slimevr.gui;

import com.jme3.math.FastMath;
import com.jme3.math.Quaternion;
import com.jme3.math.Vector3f;
import dev.slimevr.VRServer;
import dev.slimevr.gui.swing.EJBagNoStretch;
import dev.slimevr.gui.swing.EJBoxNoStretch;
import dev.slimevr.vr.trackers.*;
import io.eiren.util.StringUtils;
import io.eiren.util.ann.AWTThread;
import io.eiren.util.ann.ThreadSafe;
import io.eiren.util.collections.FastList;

import javax.swing.*;
import java.awt.*;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.util.List;
import java.util.Objects;


public class TrackersList extends EJBoxNoStretch {

        private static final String LABEL_POS = "Position:";
	private static final String LABEL_ROT = "Rotation:";
	private static final String LABEL_STATUS = "Status:";
	private static final String LABEL_TPS = "TPS:";
	private static final String LABEL_BAT = "Battery:";
	private static final String LABEL_PING = "Ping:";
	private static final String LABEL_RAW = "Raw:";
	private static final String LABEL_RAWMAG = "Raw mag:";
	private static final String LABEL_CAL = "Cal:";
	private static final String LABEL_MAGACC = "Mag acc:";
	private static final String LABEL_QUAT = "Quat:";
        private static final String LABEL_ATTFIX = "Att fix:";
        private static final String LABEL_YAWFIX = "Yaw Fix:";
        private static final String LABEL_GYROFIX = "Gyro fix:";
	private static final String LABEL_CORR = "Correction:";
	private static final String LABEL_SIG = "Signal:";
	private static final String LABEL_ROTADJ = "Rot adj:";
	private static final String LABEL_TEMP = "Temp:";
        
	private static final long UPDATE_DELAY = 50;
	private final VRServer server;
	private final VRServerGUI gui;
	private final List<TrackerPanel> trackers = new FastList<>();
	Quaternion q = new Quaternion();
	Vector3f v = new Vector3f();
	float[] angles = new float[3];
	private long lastUpdate = 0;
	private boolean debug = false;

	public TrackersList(VRServer server, VRServerGUI gui) {
		super(BoxLayout.PAGE_AXIS, false, true);
		this.server = server;
		this.gui = gui;

		setAlignmentY(TOP_ALIGNMENT);

		server.addNewTrackerConsumer(this::newTrackerAdded);
	}

	private static int getTrackerSort(Tracker t) {
		if (t instanceof ReferenceAdjustedTracker)
			t = ((ReferenceAdjustedTracker<?>) t).getTracker();
		if (t instanceof IMUTracker)
			return 0;
		if (t instanceof HMDTracker)
			return 100;
		if (t instanceof ComputedTracker)
			return 200;
		return 1000;
	}

	@AWTThread
	public void setDebug(boolean debug) {
		this.debug = debug;
		build();
	}

	@AWTThread
	private void build() {
		removeAll();

		trackers.sort((tr1, tr2) -> getTrackerSort(tr1.t) - getTrackerSort(tr2.t));

		Class<? extends Tracker> currentClass = null;

		EJBoxNoStretch line = null;
		boolean first = true;

		for (TrackerPanel tr : trackers) {
			Tracker t = tr.t;
			if (t instanceof ReferenceAdjustedTracker)
				t = ((ReferenceAdjustedTracker<?>) t).getTracker();
			if (currentClass != t.getClass()) {
				currentClass = t.getClass();
				if (line != null)
					line.add(Box.createHorizontalGlue());
				line = null;
				line = new EJBoxNoStretch(BoxLayout.LINE_AXIS, false, true);
				line.add(Box.createHorizontalGlue());
				JLabel nameLabel;
				line.add(nameLabel = new JLabel(currentClass.getSimpleName()));
				nameLabel.setFont(nameLabel.getFont().deriveFont(Font.BOLD));
				line.add(Box.createHorizontalGlue());
				add(line);
				line = null;
			}

			if (line == null) {
				line = new EJBoxNoStretch(BoxLayout.LINE_AXIS, false, true);
				add(Box.createVerticalStrut(3));
				add(line);
				first = true;
			} else {
				line.add(Box.createHorizontalStrut(3));
				first = false;
			}
			tr.build();
			line.add(tr);
			if (!first)
				line = null;
		}
		validate();
		gui.refresh();
	}

	@ThreadSafe
	public void updateTrackers() {
		if (lastUpdate + UPDATE_DELAY > System.currentTimeMillis())
			return;
		lastUpdate = System.currentTimeMillis();
		java.awt.EventQueue.invokeLater(() -> {
			for (TrackerPanel tr : trackers) {
				tr.update();
			}
		});
	}

	@ThreadSafe
	public void newTrackerAdded(Tracker t) {
		java.awt.EventQueue.invokeLater(() -> {
			trackers.add(new TrackerPanel(t));
			build();
		});
	}

	private class TrackerPanel extends EJBagNoStretch {

		final Tracker t;
		JLabel position;
		JLabel rotation;
		JLabel status;
		JLabel tps;
		JLabel bat;
		JLabel ping;
		JLabel raw;
		JLabel rawMag;
		JLabel calibration;
		JLabel magAccuracy;
		JLabel adj;
		JLabel adjYaw;
		JLabel adjGyro;
		JLabel correction;
		JLabel signalStrength;
		JLabel rotQuat;
		JLabel rotAdj;
		JLabel temperature;
                Font   monoFont;

		@AWTThread
		public TrackerPanel(Tracker t) {
			super(false, true);

			this.t = t;
		}

		@SuppressWarnings("unchecked")
		@AWTThread
		public TrackerPanel build() {
			int row = 0;
                        int col = 0;

			Tracker realTracker = t;
			if (t instanceof ReferenceAdjustedTracker)
				realTracker = ((ReferenceAdjustedTracker<? extends Tracker>) t).getTracker();
			removeAll();
			JLabel nameLabel;
			add(
				nameLabel = new JLabel(t.getDescriptiveName()),
				s(c(0, row, 2, GridBagConstraints.FIRST_LINE_START), 4, 1)
			);
			nameLabel.setFont(nameLabel.getFont().deriveFont(Font.BOLD));
			row++;

			if (t.userEditable()) {
				TrackerConfig cfg = server.getTrackerConfig(t);
				JComboBox<String> desSelect;
				add(
					desSelect = new JComboBox<>(),
					s(c(0, row, 2, GridBagConstraints.FIRST_LINE_START), 2, 1)
				);
				for (TrackerPosition p : TrackerPosition.values) {
					desSelect.addItem(p.name());
				}
				if (cfg.designation != null) {
					TrackerPosition p = TrackerPosition.getByDesignation(cfg.designation);
					if (p != null)
						desSelect.setSelectedItem(p.name());
				}
				desSelect.addActionListener(new ActionListener() {
					@Override
					public void actionPerformed(ActionEvent e) {
						TrackerPosition p = TrackerPosition
							.valueOf(String.valueOf(desSelect.getSelectedItem()));
						t.setBodyPosition(p);
						server.trackerUpdated(t);
					}
				});
				if (realTracker instanceof IMUTracker) {
					IMUTracker imu = (IMUTracker) realTracker;
					JComboBox<String> mountSelect;
					add(
						mountSelect = new JComboBox<>(),
						s(c(2, row, 2, GridBagConstraints.FIRST_LINE_START), 2, 1)
					);
					for (TrackerMountingRotation p : TrackerMountingRotation.values) {
						mountSelect.addItem(p.name());
					}

					TrackerMountingRotation selected = TrackerMountingRotation
						.fromQuaternion(imu.getMountingRotation());
					mountSelect
						.setSelectedItem(
							Objects
								.requireNonNullElse(selected, TrackerMountingRotation.BACK)
								.name()
						);
					mountSelect.addActionListener(new ActionListener() {
						@Override
						public void actionPerformed(ActionEvent e) {
							TrackerMountingRotation tr = TrackerMountingRotation
								.valueOf(String.valueOf(mountSelect.getSelectedItem()));
							imu.setMountingRotation(tr.quaternion);
							server.trackerUpdated(t);
						}
					});
				}
				row++;
			}
			if (realTracker instanceof IMUTracker) {
				add(new JLabel(LABEL_PING), c(0, row, 2, GridBagConstraints.FIRST_LINE_START));
				add(ping = new JLabel(""), c(1, row, 2, GridBagConstraints.FIRST_LINE_START));
                                add(new JLabel(LABEL_SIG), c(2, row, 2, GridBagConstraints.FIRST_LINE_START));
                                add(
					signalStrength = new JLabel(""),
					c(3, row, 2, GridBagConstraints.FIRST_LINE_START)
				);
			}
			row++;
			
			if (realTracker instanceof TrackerWithTPS) {
                            	add(new JLabel(LABEL_TPS), c(0, row, 2, GridBagConstraints.FIRST_LINE_START));
				add(tps = new JLabel(""), c(1, row, 2, GridBagConstraints.FIRST_LINE_START));
			}
			row++;
			add(new JLabel(LABEL_STATUS), c(0, row, 2, GridBagConstraints.FIRST_LINE_START));
			add(
				status = new JLabel(""),
				c(1, row, 2, GridBagConstraints.FIRST_LINE_START)
			);
			if (realTracker instanceof TrackerWithBattery) {
				add(new JLabel(LABEL_BAT), c(2, row, 2, GridBagConstraints.FIRST_LINE_START));
				add(bat = new JLabel(""), c(3, row, 2, GridBagConstraints.FIRST_LINE_START));
			}
			row++;
                        col = 0;
			if (t.hasRotation()) {
                                add(new JLabel(LABEL_ROT), c(col++, row, 2, GridBagConstraints.FIRST_LINE_START));
				add(
					rotation = new JLabel(""),
					c(col++, row, 2, GridBagConstraints.FIRST_LINE_START)
				);
                                
                        }
                        add(new JLabel(LABEL_RAW), c(col++, row, 2, GridBagConstraints.FIRST_LINE_START));
			add(raw = new JLabel(""), s(c(col++, row, 2, GridBagConstraints.FIRST_LINE_START), 3, 1));
                        row++;
                        
			if (t.hasPosition()) {
                                add(new JLabel(LABEL_POS), c(0, row, 2, GridBagConstraints.FIRST_LINE_START));
				add(
					position = new JLabel(""),
					c(1, row, 2, GridBagConstraints.FIRST_LINE_START)
				);
                                row++;
                        }
                        
			

			if (debug && realTracker instanceof IMUTracker) {
				add(new JLabel(LABEL_QUAT), c(2, row, 2, GridBagConstraints.FIRST_LINE_START));
				add(rotQuat = new JLabel(""), c(3, row, 2, GridBagConstraints.FIRST_LINE_START));
			}
			row++;

			if (debug && realTracker instanceof IMUTracker) {
				add(new JLabel(LABEL_RAWMAG), c(0, row, 2, GridBagConstraints.FIRST_LINE_START));
				add(
					rawMag = new JLabel(""),
					s(c(1, row, 2, GridBagConstraints.FIRST_LINE_START), 3, 1)
				);
				add(new JLabel(LABEL_GYROFIX), c(2, row, 2, GridBagConstraints.FIRST_LINE_START));
				add(
					new JLabel(String.format("0x%8x", realTracker.hashCode())),
					s(c(3, row, 2, GridBagConstraints.FIRST_LINE_START), 3, 1)
				);
				row++;
				add(new JLabel(LABEL_CAL), c(0, row, 2, GridBagConstraints.FIRST_LINE_START));
				add(
					calibration = new JLabel(""),
					c(1, row, 2, GridBagConstraints.FIRST_LINE_START)
				);
				add(new JLabel(LABEL_MAGACC), c(2, row, 2, GridBagConstraints.FIRST_LINE_START));
				add(
					magAccuracy = new JLabel(""),
					c(3, row, 2, GridBagConstraints.FIRST_LINE_START)
				);
				row++;
				add(new JLabel(LABEL_CORR), c(0, row, 2, GridBagConstraints.FIRST_LINE_START));
				add(
					correction = new JLabel(""),
					s(c(1, row, 2, GridBagConstraints.FIRST_LINE_START), 3, 1)
				);
				add(new JLabel(LABEL_ROTADJ), c(2, row, 2, GridBagConstraints.FIRST_LINE_START));
				add(rotAdj = new JLabel(""), c(3, row, 2, GridBagConstraints.FIRST_LINE_START));
				row++;
			}

			if (debug && t instanceof ReferenceAdjustedTracker) {
				add(new JLabel(LABEL_ATTFIX), c(0, row, 2, GridBagConstraints.FIRST_LINE_START));
				add(adj = new JLabel(""), c(1, row, 2, GridBagConstraints.FIRST_LINE_START));
				add(new JLabel(LABEL_YAWFIX), c(2, row, 2, GridBagConstraints.FIRST_LINE_START));
				add(
					adjYaw = new JLabel(""),
					c(3, row, 2, GridBagConstraints.FIRST_LINE_START)
				);
				row++;
				add(new JLabel(LABEL_GYROFIX), c(0, row, 2, GridBagConstraints.FIRST_LINE_START));
				add(
					adjGyro = new JLabel(""),
					c(1, row, 2, GridBagConstraints.FIRST_LINE_START)
				);
				add(new JLabel(LABEL_TEMP), c(2, row, 2, GridBagConstraints.FIRST_LINE_START));
				add(
					temperature = new JLabel(""),
					c(3, row, 2, GridBagConstraints.FIRST_LINE_START)
				);
			}
                        
                        monoFont = new Font(Font.MONOSPACED, Font.BOLD, (int)(12 * gui.getZoom()));
                        
                        if (position != null) position.setFont(monoFont);
                        if (rotation != null) rotation.setFont(monoFont);
                        status.setFont(monoFont);
                        tps.setFont(monoFont);
                        if (bat != null) bat.setFont(monoFont);
                        if (ping != null) ping.setFont(monoFont);
                        raw.setFont(monoFont);
                        if (rawMag != null) rawMag.setFont(monoFont);
                        if (calibration != null) calibration.setFont(monoFont);
                        if (magAccuracy != null) magAccuracy.setFont(monoFont);
                        if (adj != null) adj.setFont(monoFont);
                        if (adjYaw != null) adjYaw.setFont(monoFont);
                        if (adjGyro != null) adjGyro.setFont(monoFont);
                        if (correction != null) correction.setFont(monoFont);
                        if (signalStrength != null) signalStrength.setFont(monoFont);
                        if (rotQuat != null) rotQuat.setFont(monoFont);
                        if (rotAdj != null) rotAdj.setFont(monoFont);
                        if (temperature != null) temperature.setFont(monoFont);
                        
			setBorder(BorderFactory.createLineBorder(new Color(0x663399), 2, false));
			TrackersList.this.add(this);
			return this;
		}

		@SuppressWarnings("unchecked")
		@AWTThread
		public void update() {
			if (position == null && rotation == null)
				return;
			Tracker realTracker = t;
			if (t instanceof ReferenceAdjustedTracker)
				realTracker = ((ReferenceAdjustedTracker<? extends Tracker>) t).getTracker();
			t.getRotation(q);
			t.getPosition(v);
			q.toAngles(angles);

			if (position != null)
				position
					.setText(
						String.format("%6s", StringUtils.prettyNumber(v.x, 1))
							+ " "
							+ String.format("%6s", StringUtils.prettyNumber(v.y, 1))
							+ " "
							+ String.format("%6s", StringUtils.prettyNumber(v.z, 1))
					);
			if (rotation != null)
				rotation
                                        .setText(
                                                String.format("%4s", StringUtils.prettyNumber(angles[0] * FastMath.RAD_TO_DEG, 0))
                                                        + " " + 
                                                        String.format("%4s", StringUtils.prettyNumber(angles[1] * FastMath.RAD_TO_DEG, 0))
                                                        + " " + 
                                                        String.format("%4s", StringUtils.prettyNumber(angles[2] * FastMath.RAD_TO_DEG, 0))
                                                        + " "
					);
			status.setText(String.format("%-5s", t.getStatus().toString().toLowerCase()) + " ");

			if (realTracker instanceof TrackerWithTPS) {
				tps.setText(StringUtils.prettyNumber(((TrackerWithTPS) realTracker).getTPS(), 1));
			}
			if (realTracker instanceof TrackerWithBattery) {
				TrackerWithBattery twb = (TrackerWithBattery) realTracker;
				float level = twb.getBatteryLevel();
				float voltage = twb.getBatteryVoltage();
				if (level == 0.0f) {
					bat.setText(String.format("%sV", StringUtils.prettyNumber(voltage, 2)));
				} else if (voltage == 0.0f) {
					bat.setText(String.format("%d%%", Math.round(level)));
				} else {
					bat
						.setText(
							String
								.format(
									"%d%% (%sV)",
									Math.round(level),
									StringUtils.prettyNumber(voltage, 2)
								)
						);
				}
			}
			if (t instanceof ReferenceAdjustedTracker) {
				ReferenceAdjustedTracker<Tracker> rat = (ReferenceAdjustedTracker<Tracker>) t;
				if (adj != null) {
					rat.attachmentFix.toAngles(angles);
					adj
						.setText(
							StringUtils.prettyNumber(angles[0] * FastMath.RAD_TO_DEG, 0)
								+ " "
								+ StringUtils.prettyNumber(angles[1] * FastMath.RAD_TO_DEG, 0)
								+ " "
								+ StringUtils.prettyNumber(angles[2] * FastMath.RAD_TO_DEG, 0)
						);
				}
				if (adjYaw != null) {
					rat.yawFix.toAngles(angles);
					adjYaw
						.setText(
							StringUtils.prettyNumber(angles[0] * FastMath.RAD_TO_DEG, 0)
								+ " "
								+ StringUtils.prettyNumber(angles[1] * FastMath.RAD_TO_DEG, 0)
								+ " "
								+ StringUtils.prettyNumber(angles[2] * FastMath.RAD_TO_DEG, 0)
						);
				}
				if (adjGyro != null) {
					rat.gyroFix.toAngles(angles);
					adjGyro
						.setText(
							StringUtils.prettyNumber(angles[0] * FastMath.RAD_TO_DEG, 0)
								+ " "
								+ StringUtils.prettyNumber(angles[1] * FastMath.RAD_TO_DEG, 0)
								+ " "
								+ StringUtils.prettyNumber(angles[2] * FastMath.RAD_TO_DEG, 0)
						);
				}
			}
			if (realTracker instanceof IMUTracker) {
				if (ping != null)
					ping.setText(String.valueOf(((IMUTracker) realTracker).ping));
				if (signalStrength != null) {
					int signal = ((IMUTracker) realTracker).signalStrength;
					if (signal == -1) {
						signalStrength.setText("N/A");
					} else {
						// -40 dBm is excellent, -95 dBm is very poor
						int percentage = (signal - -95) * (100 - 0) / (-40 - -95) + 0;
						percentage = Math.max(Math.min(percentage, 100), 0);
						signalStrength.setText(percentage + "% " + "(" + signal + " dBm" + ")");
					}
				}
			}
			realTracker.getRotation(q);
			q.toAngles(angles);
			raw
				.setText(
                                        String.format("%4s", StringUtils.prettyNumber(angles[0] * FastMath.RAD_TO_DEG, 0))
                                            + " " + 
                                            String.format("%4s", StringUtils.prettyNumber(angles[1] * FastMath.RAD_TO_DEG, 0))
                                            + " " + 
                                            String.format("%4s", StringUtils.prettyNumber(angles[2] * FastMath.RAD_TO_DEG, 0))
                                            + " "
				);
			if (realTracker instanceof IMUTracker) {
				IMUTracker imu = (IMUTracker) realTracker;
				if (rawMag != null) {
					imu.rotMagQuaternion.toAngles(angles);
					rawMag
						.setText(
							StringUtils.prettyNumber(angles[0] * FastMath.RAD_TO_DEG, 0)
								+ " "
								+ StringUtils.prettyNumber(angles[1] * FastMath.RAD_TO_DEG, 0)
								+ " "
								+ StringUtils.prettyNumber(angles[2] * FastMath.RAD_TO_DEG, 0)
						);
				}
				if (calibration != null)
					calibration.setText(imu.calibrationStatus + " / " + imu.magCalibrationStatus);
				if (magAccuracy != null)
					magAccuracy
						.setText(
							StringUtils
								.prettyNumber(imu.magnetometerAccuracy * FastMath.RAD_TO_DEG, 1)
								+ "°"
						);
				if (correction != null) {
					imu.getCorrection(q);
					q.toAngles(angles);
					correction
						.setText(
							StringUtils.prettyNumber(angles[0] * FastMath.RAD_TO_DEG, 0)
								+ " "
								+ StringUtils.prettyNumber(angles[1] * FastMath.RAD_TO_DEG, 0)
								+ " "
								+ StringUtils.prettyNumber(angles[2] * FastMath.RAD_TO_DEG, 0)
						);
				}
				if (rotQuat != null) {
					imu.rotQuaternion.toAngles(angles);
					rotQuat
						.setText(
							StringUtils.prettyNumber(angles[0] * FastMath.RAD_TO_DEG, 0)
								+ " "
								+ StringUtils.prettyNumber(angles[1] * FastMath.RAD_TO_DEG, 0)
								+ " "
								+ StringUtils.prettyNumber(angles[2] * FastMath.RAD_TO_DEG, 0)
						);
				}
				if (rotAdj != null) {
					imu.rotAdjust.toAngles(angles);
					rotAdj
						.setText(
							StringUtils.prettyNumber(angles[0] * FastMath.RAD_TO_DEG, 0)
								+ " "
								+ StringUtils.prettyNumber(angles[1] * FastMath.RAD_TO_DEG, 0)
								+ " "
								+ StringUtils.prettyNumber(angles[2] * FastMath.RAD_TO_DEG, 0)
						);
				}
				if (temperature != null) {
					if (imu.temperature == 0.0f) {
						// Can't be exact 0, so no info received
						temperature.setText("?");
					} else {
						temperature.setText(StringUtils.prettyNumber(imu.temperature, 1) + "∘C");
					}
				}
			}
		}
	}
}

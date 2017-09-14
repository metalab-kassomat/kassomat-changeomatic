package at.metalab.changeomatic;

import java.awt.Color;
import java.awt.Font;
import java.awt.FontFormatException;
import java.awt.GraphicsEnvironment;
import java.awt.GridLayout;
import java.awt.font.TextAttribute;
import java.io.IOException;
import java.util.Collections;
import java.util.HashMap;
import java.util.Map;
import java.util.logging.Logger;

import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.SwingConstants;

public class ChangeomaticFrame extends javax.swing.JFrame {

	private final static Logger LOG = Logger.getLogger(ChangeomaticFrame.class.getCanonicalName());
	
	private static final long serialVersionUID = 1L;

	private final JLabel euro5;
	private final JLabel euro10;
	private final JLabel euro20;
	private final JLabel euro50;
	private final JLabel euro100;
	private final JLabel euro200;

	private final JLabel hint;

	private final JPanel notes;

	private final Map<Integer, JLabel> labelsByChannel = Collections
			.synchronizedMap(new HashMap<Integer, JLabel>());

	private JLabel createNoteLabel(String amount) {
		JLabel note = new JLabel();
		note.setFont(fontNoteInhibited);
		note.setText(amount);
		note.setHorizontalAlignment(SwingConstants.CENTER);
		note.setForeground(Color.RED);

		return note;
	}

	private final JLabel emptiedAmount;
	
	private static Font loadFont(String resource) {
		try {
			return Font.createFont(Font.TRUETYPE_FONT, Thread.currentThread()
					.getContextClassLoader().getResourceAsStream(resource));
		} catch (IOException | FontFormatException e) {
			// Handle exception
			e.printStackTrace();
			return null;
		}
	}

	private Font font;

	private Font fontNoteAccepted;

	private Font fontNoteInhibited;

	@SuppressWarnings("unchecked")
	public ChangeomaticFrame() {
		setDefaultCloseOperation(javax.swing.WindowConstants.EXIT_ON_CLOSE);
		setTitle("change-o-matic");
		setCursor(new java.awt.Cursor(java.awt.Cursor.DEFAULT_CURSOR));

		font = loadFont("alphbeta.ttf");

		fontNoteAccepted = font.deriveFont(150f);

		{
			@SuppressWarnings("rawtypes")
			Map attributes = fontNoteAccepted.getAttributes();
			attributes.put(TextAttribute.STRIKETHROUGH,
					TextAttribute.STRIKETHROUGH_ON);
			fontNoteInhibited = fontNoteAccepted.deriveFont(attributes);
		}

		JPanel main;
		{
			main = new JPanel(new GridLayout(3, 0));
			main.setBackground(Color.BLACK);

			{
				JLabel logo = new JLabel();
				logo.setHorizontalAlignment(SwingConstants.CENTER);
				logo.setForeground(Color.WHITE);
				logo.setFont(font.deriveFont(110f));
				logo.setText("change-o-matic".toUpperCase());

				main.add(logo);
			}

			euro5 = createNoteLabel("5");
			euro10 = createNoteLabel("10");
			euro20 = createNoteLabel("20");
			euro50 = createNoteLabel("50");
			euro100 = createNoteLabel("100");
			euro200 = createNoteLabel("200");

			labelsByChannel.put(1, euro5);
			labelsByChannel.put(2, euro10);
			labelsByChannel.put(3, euro20);
			labelsByChannel.put(4, euro50);
			labelsByChannel.put(5, euro100);
			labelsByChannel.put(6, euro200);

			// mark all as inhibited initially
			for (JLabel note : labelsByChannel.values()) {
				makeInhibited(note);
			}

			notes = new JPanel(new GridLayout(1, 0));
			notes.setBackground(Color.BLACK);

			notes.add(euro5);
			notes.add(euro10);
			notes.add(euro20);
			// notes.add(euro50);
			// notes.add(euro100);
			// notes.add(euro200);

			main.add(notes);

			hint = new JLabel();
			hint.setForeground(Color.YELLOW);
			hint.setFont(font.deriveFont(80f));
			hint.setHorizontalAlignment(SwingConstants.CENTER);
			main.add(hint);
		}

		JPanel maintenance;
		{
			maintenance = new JPanel(new GridLayout(3, 0));
			maintenance.setBackground(Color.BLACK);

			{
				JLabel logo = new JLabel();
				logo.setHorizontalAlignment(SwingConstants.CENTER);
				logo.setForeground(Color.WHITE);
				logo.setFont(font.deriveFont(110f));
				logo.setText("change-o-matic".toUpperCase());

				maintenance.add(logo);
			}
			
			emptiedAmount = new JLabel();
			emptiedAmount.setForeground(Color.YELLOW);
			emptiedAmount.setFont(font.deriveFont(80f));
			emptiedAmount.setHorizontalAlignment(SwingConstants.CENTER);
			maintenance.add(emptiedAmount);
		}
		
		/** disabled maintenance for now
		JTabbedPane tabbedPane = new JTabbedPane();
		tabbedPane.add(main, 0);
		tabbedPane.setTitleAt(0, "change-o-matic");

		tabbedPane.add(maintenance, 1);
		tabbedPane.setTitleAt(1, "maintenance");
		
		add(tabbedPane);
		*/
		
		add(main);
		
		setSize(GraphicsEnvironment.getLocalGraphicsEnvironment()
				.getMaximumWindowBounds().getSize());

		setSize(1024, 768);
		setLocationRelativeTo(null);
	}
	
	public void updateEmptiedAmount(String amount) {
		emptiedAmount.setText(amount);
		emptiedAmount.repaint();
	}
	
	private void updateHint(String strHint) {
		LOG.info("updateHint: " + strHint);
		
		hint.setText("- " + strHint + " -");
		hint.repaint();
	}

	public void hintOhNo() {
		notes.setVisible(false);
		updateHint("OUT OF ORDER");
	}

	public void hintSorry() {
		updateHint("SORRY. CAN'T DO.");
	}

	public void hintInsertNote() {
		notes.setVisible(true);
		updateHint("INSERT NOTE");
	}

	public void hintPleaseWait() {
		notes.setVisible(false);
		updateHint("PLEASE WAIT");
	}

	public void hintDispensing() {
		notes.setVisible(false);
		updateHint("DISPENSING");
	}
	
	public void hintNoCoins() {
		updateHint("NO COINS");
	}

	public void updateInhibit(int channel, boolean inhibited) {
		JLabel note = labelsByChannel.get(channel);
		if (inhibited) {
			makeInhibited(note);
		} else {
			makeAccepted(note);
		}

		updateHint();
	}

	public void updateHint() {
		boolean outOfOrder = true;
		for (JLabel label : labelsByChannel.values()) {
			if (label.getForeground().equals(Color.GREEN)) {
				outOfOrder = false;
			}
		}

		if (outOfOrder) {
			hintOhNo();
		} else {
			hintInsertNote();
		}
	}

	private void makeInhibited(JLabel note) {
		LOG.info("makeInhibited: amount=" + note.getText());
		
		note.setForeground(Color.RED);
		note.setFont(fontNoteInhibited);
	}

	private void makeAccepted(JLabel note) {
		LOG.info("makeAccepted: amount=" + note.getText());

		note.setForeground(Color.GREEN);
		note.setFont(fontNoteAccepted);
	}

}

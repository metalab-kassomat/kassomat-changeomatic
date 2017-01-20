package at.metalab.changeomatic;

import java.math.BigDecimal;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.logging.Logger;

import org.redisson.Config;
import org.redisson.Redisson;
import org.redisson.RedissonClient;
import org.redisson.core.MessageListener;
import org.redisson.core.RTopic;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonInclude.Include;
import com.fasterxml.jackson.databind.ObjectMapper;

public class ChangeomaticMain {

	private final static Logger LOG = Logger.getLogger(ChangeomaticMain.class
			.getCanonicalName());

	private final static ObjectMapper om = new ObjectMapper();

	private final static KassomatCallbackDispatcher HOPPER_RESPONSE_DISPATCHER = new KassomatCallbackDispatcher();

	private final static KassomatCallbackDispatcher VALIDATOR_RESPONSE_DISPATCHER = new KassomatCallbackDispatcher();

	@JsonInclude(Include.NON_NULL)
	@JsonIgnoreProperties(ignoreUnknown = true)
	public static class ChangeomaticJson {
		public String msgId;
		public String event;
		public Integer channel;
		public Integer amount;
		public List<Integer> inhibitedChannels;

		public String stringify() {
			try {
				return om.writeValueAsString(this);
			} catch (Exception exception) {
				throw new RuntimeException("stringify failed", exception);
			}
		}

		public static ChangeomaticJson parse(String json) {
			try {
				return om.readValue(json, ChangeomaticJson.class);
			} catch (Exception exception) {
				throw new RuntimeException("parse failed", exception);
			}
		}
	}

	@JsonInclude(Include.NON_NULL)
	@JsonIgnoreProperties(ignoreUnknown = true)
	public static class KassomatJson {
		public String cmd;
		public String event;
		public String msgId;
		public String correlId;
		public Integer amount;
		public String error;
		public String result;
		public String cc;
		public String channel;
		public String channels;
		public String id;

		public String stringify() {
			try {
				return om.writeValueAsString(this).replaceAll(" ", "");
			} catch (Exception exception) {
				throw new RuntimeException("stringify failed", exception);
			}
		}

		public static KassomatJson parse(String json) {
			try {
				return om.readValue(json, KassomatJson.class);
			} catch (Exception exception) {
				throw new RuntimeException("parse failed", exception);
			}
		}
	}

	private static class KassomatCallbackDispatcher implements
			MessageListener<String> {

		private Map<String, KassomatRequestCallback> callbacks = Collections
				.synchronizedMap(new HashMap<String, KassomatRequestCallback>());

		public void onMessage(String topic, String strMessage) {
			try {
				LOG.info("received message in topic '" + topic + "':" + strMessage);
				
				KassomatJson message = KassomatJson.parse(strMessage);
				String correlId = message.correlId;

				KassomatRequestCallback callback = callbacks.remove(correlId);
				if (callback != null) {
					LOG.info("dispatching to callback: " + strMessage);
					try {
						callback.handleMessage(topic, message);
					} catch (RuntimeException runtimeException) {
						LOG.warning("caught runtimeException from handleMessage: correlId="
								+ message.correlId);
					}
				}
			} catch (Exception exception) {
				oops("KassomatCallbackDispatcher", exception);
			}
		}

		public void registerCallback(KassomatRequestCallback callback) {
			callbacks.put(callback.getCorrelId(), callback);
		}
	}

	public static void main(String[] args) throws Exception {
		Config c = new Config();
		c.setCodec(org.redisson.client.codec.StringCodec.INSTANCE);
		c.useSingleServer().setAddress("127.0.0.1:6379");

		RedissonClient r = Redisson.create(c);

		final RTopic<String> changeomaticEvent = r.getTopic("changeomatic-event");
		changeomaticEvent.publish(createChangeomaticEvent("starting-up").stringify());

		final RTopic<String> payoutEvent = r.getTopic("payout-event");
		
		final RTopic<String> hopperRequest = r.getTopic("hopper-request");
		final RTopic<String> hopperResponse = r.getTopic("hopper-response");
		final RTopic<String> hopperEvent = r.getTopic("hopper-event");

		final RTopic<String> validatorEvent = r.getTopic("validator-event");
		final RTopic<String> validatorRequest = r.getTopic("validator-request");
		final RTopic<String> validatorResponse = r.getTopic("validator-response");

		hopperResponse.addListener(HOPPER_RESPONSE_DISPATCHER);
		validatorResponse.addListener(VALIDATOR_RESPONSE_DISPATCHER);

		final ChangeomaticFrame changeomaticFrame = new ChangeomaticFrame();
		startupGui(changeomaticFrame);

		synchronized (changeomaticFrame) {
			changeomaticFrame.wait();
			// GUI available
			changeomaticEvent.publish(createChangeomaticEvent("started")
					.stringify());
		}

		changeomaticFrame.hintPleaseWait();

		// initial money check
		submitAllTestPayouts(changeomaticFrame, hopperRequest, hopperResponse,
				validatorRequest, changeomaticEvent);

		payoutEvent.addListener(new MessageListener<String>() {

			public void onMessage(String channel, String strMessage) {
				try {
					LOG.info("payout-event: " + strMessage);
					KassomatJson message = KassomatJson.parse(strMessage);

					switch (message.event) {
					case "started":
						submitAllTestPayouts(changeomaticFrame, hopperRequest, hopperResponse,
								validatorRequest, changeomaticEvent);
						break;
						
					case "exiting":
						changeomaticFrame.hintSorry();
						changeomaticFrame.repaint();
						break;
					}
				} catch (Exception exception) {
					oops("payout-event-listener", exception);
				}
			}
		});
		
		// handle events which have happened in the banknote validator
		validatorEvent.addListener(new MessageListener<String>() {

			public void onMessage(String channel, String strMessage) {
				try {
					LOG.info("validator-event: " + strMessage);
					KassomatJson message = KassomatJson.parse(strMessage);

					switch (message.event) {
					case "credit":
						validatorRequest.publishAsync(inhibitAllChannels()
								.stringify());

						validatorRequest.publishAsync(disable().stringify());

						hopperRequest.publishAsync(doPayout(message.amount)
								.stringify());
						break;

					case "read":
						break;

					case "reading":
						changeomaticFrame.hintPleaseWait();
						changeomaticFrame.repaint();
						break;

					case "rejecting":
						changeomaticFrame.hintSorry();
						changeomaticFrame.repaint();
						break;

					case "rejected":
						changeomaticFrame.updateHint();
						changeomaticFrame.repaint();
						break;
					}
				} catch (Exception exception) {
					oops("validator-event-listener", exception);
				}
			}
		});

		// handle events which have happened in the coin hopper
		hopperEvent.addListener(new MessageListener<String>() {
			public void onMessage(String channel, String strMessage) {
				try {
					LOG.info("hopper-event: " + strMessage);
					KassomatJson message = KassomatJson.parse(strMessage);

					switch (message.event) {
					case "disabled":
						hopperRequest.publishAsync(enable().stringify());
						break;

					case "dispensing":
						changeomaticFrame.hintDispensing();
						changeomaticFrame.repaint();
						break;

					case "smart emptied":
					case "smart emptying":
						if(message.amount != null) {
							changeomaticFrame.updateEmptiedAmount(
									new BigDecimal(message.amount.intValue()).movePointLeft(2).toPlainString());
						}
						break;
						
					case "floated":
					case "cashbox paid":
						changeomaticFrame.hintPleaseWait();
						changeomaticFrame.repaint();

						validatorRequest.publishAsync(inhibitAllChannels()
								.stringify());

						submitAllTestPayouts(changeomaticFrame, hopperRequest,
								hopperResponse, validatorRequest,
								changeomaticEvent);

						validatorRequest.publishAsync(enable().stringify());
						break;

					case "coin credit":
						submitAllTestPayouts(changeomaticFrame, hopperRequest,
								hopperResponse, validatorRequest,
								changeomaticEvent);
						break;
					}
				} catch (Exception exception) {
					oops("hopper-event-listener", exception);
				}
			}
		});

		LOG.info("change-o-matic is open for business :D");
		LOG.info("press enter to exit...");
		System.in.read();

		r.shutdown();
	}

	private static void submitAllTestPayouts(
			final ChangeomaticFrame changeomaticFrame,
			final RTopic<String> hopperRequest,
			final RTopic<String> hopperResponse,
			final RTopic<String> validatorRequest,
			final RTopic<String> changeomaticEvent) {
		LOG.info("entered submitAllTestPayouts");
		
		submitTestPayout(changeomaticFrame, 500, 1, hopperRequest,
				hopperResponse, validatorRequest, changeomaticEvent);

		submitTestPayout(changeomaticFrame, 1000, 2, hopperRequest,
				hopperResponse, validatorRequest, changeomaticEvent);

		submitTestPayout(changeomaticFrame, 2000, 3, hopperRequest,
				hopperResponse, validatorRequest, changeomaticEvent);

//		submitTestPayout(changeomaticFrame, 5000, 4, hopperRequest,
//				hopperResponse, validatorRequest, changeomaticEvent);

		/*
		 * submitTestPayout(changeomaticFrame, inhibits, 10000, 5,
		 * hopperRequest, hopperResponse, validatorRequest, changeomaticEvent);
		 * 
		 * submitTestPayout(changeomaticFrame, inhibits, 20000, 6,
		 * hopperRequest, hopperResponse, validatorRequest, changeomaticEvent);
		 * 
		 * submitTestPayout(changeomaticFrame, inhibits, 50000, 7,
		 * hopperRequest, hopperResponse, validatorRequest, changeomaticEvent);
		 */
	}

	private static synchronized void submitTestPayout(
			final ChangeomaticFrame changeomaticFrame, final int amount,
			final int channel, final RTopic<String> hopperRequest,
			final RTopic<String> hopperResponse,
			final RTopic<String> validatorRequest,
			final RTopic<String> changeomaticEvent) {
		final KassomatJson tp = testPayout(amount);

		final KassomatRequestCallback cb = new KassomatRequestCallback(tp.msgId) {

			@Override
			public void handleMessage(String topic, KassomatJson message) {
				if ("ok".equals(message.result)) {
					LOG.info("received OK for amount: " + amount);
					
					// we can change this banknote
					
					// update the UI
					changeomaticFrame.updateInhibit(channel, false);

					// enable the channel in the note validator
					validatorRequest.publishAsync(enableChannel(channel)
							.stringify());
				} else {
					LOG.info("received NO for amount: " + amount);

					// nope, this banknote cannot be changed

					// update the UI
					changeomaticFrame.updateInhibit(channel, true);
					
					// disable the channel in the note validator
					validatorRequest.publishAsync(disableChannel(channel)
							.stringify());
				}

				changeomaticFrame.repaint();
			}
		};

		LOG.info("publishing test for amount: " + amount);

		HOPPER_RESPONSE_DISPATCHER.registerCallback(cb);
		hopperRequest.publishAsync(tp.stringify());
	}

	public abstract static class KassomatRequestCallback implements
			MessageListener<String> {

		private String correlId;

		public KassomatRequestCallback(String msgId) {
			this.correlId = msgId;
		}

		public void onMessage(String topic, String strMessage) {
			try {
				KassomatJson message = KassomatJson.parse(strMessage);
				if (correlId.equals(message.correlId)) {
					LOG.info("hopper-response: " + strMessage);
					handleMessage(topic, message);
				}
			} catch (Exception exception) {
				oops("KassomatRequestCallback", exception);
			}
		}

		public abstract void handleMessage(String topic, KassomatJson message);

		public String getCorrelId() {
			return correlId;
		}
	}

	private static void oops(String text, Exception exception) {
		System.out.println("oops: " + exception.getMessage());
		exception.printStackTrace();
	}

	private static ChangeomaticJson createChangeomaticEvent(String event) {
		ChangeomaticJson c = new ChangeomaticJson();
		c.event = event;
		c.msgId = UUID.randomUUID().toString();

		return c;
	}

	private static KassomatJson createSmartPayoutRequest(String cmd) {
		KassomatJson k = new KassomatJson();
		k.cmd = cmd;
		k.msgId = UUID.randomUUID().toString();

		return k;
	}

	private static KassomatJson doPayout(int amount) {
		KassomatJson k = createSmartPayoutRequest("do-payout");
		k.amount = amount;

		return k;
	}

	private static KassomatJson testPayout(int amount) {
		KassomatJson k = createSmartPayoutRequest("test-payout");
		k.amount = amount;

		return k;
	}

	private static KassomatJson inhibitAllChannels() {
		KassomatJson k = createSmartPayoutRequest("disable-channels");
		k.channels = "1,2,3,4,5,6,7,8";
		return k;
	}

	private static KassomatJson enableChannel(int channel) {
		KassomatJson k = createSmartPayoutRequest("enable-channels");
		k.channels = String.valueOf(channel);
		return k;
	}

	private static KassomatJson disableChannel(int channel) {
		KassomatJson k = createSmartPayoutRequest("disable-channels");
		k.channels = String.valueOf(channel);
		return k;
	}

	private static KassomatJson disable() {
		KassomatJson k = createSmartPayoutRequest("disable");
		return k;
	}

	private static KassomatJson enable() {
		KassomatJson k = createSmartPayoutRequest("enable");
		return k;
	}

	private static void startupGui(ChangeomaticFrame changeomaticFrame) {
		/* Set the Nimbus look and feel */
		// <editor-fold defaultstate="collapsed"
		// desc=" Look and feel setting code (optional) ">
		/*
		 * If Nimbus (introduced in Java SE 6) is not available, stay with the
		 * default look and feel. For details see
		 * http://download.oracle.com/javase
		 * /tutorial/uiswing/lookandfeel/plaf.html
		 */
		try {
			for (javax.swing.UIManager.LookAndFeelInfo info : javax.swing.UIManager
					.getInstalledLookAndFeels()) {
				if ("Nimbus".equals(info.getName())) {
					javax.swing.UIManager.setLookAndFeel(info.getClassName());
					break;
				}
			}
		} catch (ClassNotFoundException ex) {
			java.util.logging.Logger.getLogger(
					ChangeomaticFrame.class.getName()).log(
					java.util.logging.Level.SEVERE, null, ex);
		} catch (InstantiationException ex) {
			java.util.logging.Logger.getLogger(
					ChangeomaticFrame.class.getName()).log(
					java.util.logging.Level.SEVERE, null, ex);
		} catch (IllegalAccessException ex) {
			java.util.logging.Logger.getLogger(
					ChangeomaticFrame.class.getName()).log(
					java.util.logging.Level.SEVERE, null, ex);
		} catch (javax.swing.UnsupportedLookAndFeelException ex) {
			java.util.logging.Logger.getLogger(
					ChangeomaticFrame.class.getName()).log(
					java.util.logging.Level.SEVERE, null, ex);
		}

		/* Create and display the form */
		java.awt.EventQueue.invokeLater(new Runnable() {
			public void run() {
				changeomaticFrame.setVisible(true);
				synchronized (changeomaticFrame) {
					changeomaticFrame.notify();
				}
			}
		});
	}
}
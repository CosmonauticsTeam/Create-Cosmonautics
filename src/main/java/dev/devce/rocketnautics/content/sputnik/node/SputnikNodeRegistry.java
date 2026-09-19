package dev.devce.rocketnautics.content.sputnik.node;

import dev.devce.rocketnautics.api.peripherals.IPeripheral;
import dev.devce.rocketnautics.api.peripherals.PeripheralRegistry;
import dev.devce.rocketnautics.content.blocks.gyrodyne.GyrodyneMode;
import dev.devce.rocketnautics.content.sputnik.model.PinType;
import dev.devce.rocketnautics.content.sputnik.model.SputnikNode;
import dev.devce.rocketnautics.content.sputnik.model.SputnikPin;
import imgui.ImColor;

import java.util.*;

public final class SputnikNodeRegistry {
    private static final Map<String, INodeHandler> REGISTRY = new LinkedHashMap<>();
    private static final Map<String, List<INodeHandler>> BY_CATEGORY = new LinkedHashMap<>();

    static {
        register(new ConstantNumberHandler());
        register(new ConstantStringHandler());
        register(new ConstantBooleanHandler());

        register(new MathAddHandler());
        register(new MathSubtractHandler());
        register(new MathMultiplyHandler());
        register(new MathDivideHandler());

        register(new LogicCompareHandler());
        register(new LogicAndHandler());
        register(new LogicOrHandler());
        register(new LogicNotHandler());

        register(new SensorAltitudeHandler());
        register(new SensorVelocityHandler());
        register(new SensorAttitudeHandler());

        register(new DisplayHandler("display", "Display", ImColor.rgb(40, 140, 110)));
        register(new DisplayHandler("display_bridge", "Display Bridge", ImColor.rgb(168, 72, 72)));
        register(new SputnikLinkHandler());
        register(new SatelliteCommsHandler());

        register(new EngineIgnitionHandler());
        register(new EngineThrustHandler());
        register(new EngineVectorHandler());
        register(new GyrodyneControlHandler());
    }

    public static void register(INodeHandler handler) {
        REGISTRY.put(handler.getTypeId(), handler);
        BY_CATEGORY.computeIfAbsent(handler.getCategory(), k -> new ArrayList<>()).add(handler);
    }

    public static INodeHandler get(String typeId) {
        return REGISTRY.get(typeId);
    }

    public static Collection<INodeHandler> getAll() {
        return Collections.unmodifiableCollection(REGISTRY.values());
    }

    public static Map<String, List<INodeHandler>> getByCategory() {
        return Collections.unmodifiableMap(BY_CATEGORY);
    }

    private static class ConstantNumberHandler implements INodeHandler {
        public String getTypeId() { return "constant_number"; }
        public String getTitle() { return "Number"; }
        public String getCategory() { return "Constants"; }
        public int getHeaderColor() { return ImColor.rgb(43, 130, 150); }
        public List<SputnikPin> createInputs() { return List.of(); }
        public List<SputnikPin> createOutputs() {
            return List.of(new SputnikPin("val", "Number", PinType.NUMBER, false));
        }
        public void execute(SputnikNode node, NodeExecutionContext ctx) {
            ctx.setOutput("val", node.getCustomNumber());
        }
    }

    private static class ConstantStringHandler implements INodeHandler {
        public String getTypeId() { return "constant_string"; }
        public String getTitle() { return "String"; }
        public String getCategory() { return "Constants"; }
        public int getHeaderColor() { return ImColor.rgb(43, 130, 150); }
        public List<SputnikPin> createInputs() { return List.of(); }
        public List<SputnikPin> createOutputs() {
            return List.of(new SputnikPin("val", "String", PinType.STRING, false));
        }
        public void execute(SputnikNode node, NodeExecutionContext ctx) {
            ctx.setOutput("val", node.getCustomString());
        }
    }

    private static class ConstantBooleanHandler implements INodeHandler {
        public String getTypeId() { return "constant_boolean"; }
        public String getTitle() { return "Boolean"; }
        public String getCategory() { return "Constants"; }
        public int getHeaderColor() { return ImColor.rgb(43, 130, 150); }
        public List<SputnikPin> createInputs() { return List.of(); }
        public List<SputnikPin> createOutputs() {
            return List.of(new SputnikPin("val", "Bool", PinType.BOOLEAN, false));
        }
        public void execute(SputnikNode node, NodeExecutionContext ctx) {
            ctx.setOutput("val", node.getCustomNumber() > 0.5);
        }
    }

    private static class MathAddHandler implements INodeHandler {
        public String getTypeId() { return "math_add"; }
        public String getTitle() { return "Add"; }
        public String getCategory() { return "Math"; }
        public int getHeaderColor() { return ImColor.rgb(62, 142, 74); }
        public List<SputnikPin> createInputs() {
            return List.of(
                    new SputnikPin("a", "A", PinType.NUMBER, true),
                    new SputnikPin("b", "B", PinType.NUMBER, true)
            );
        }
        public List<SputnikPin> createOutputs() {
            return List.of(new SputnikPin("out", "Result", PinType.NUMBER, false));
        }
        public void execute(SputnikNode node, NodeExecutionContext ctx) {
            double a = ctx.getInputAsDouble("a", 0.0);
            double b = ctx.getInputAsDouble("b", 0.0);
            ctx.setOutput("out", a + b);
        }
    }

    private static class MathSubtractHandler implements INodeHandler {
        public String getTypeId() { return "math_subtract"; }
        public String getTitle() { return "Subtract"; }
        public String getCategory() { return "Math"; }
        public int getHeaderColor() { return ImColor.rgb(62, 142, 74); }
        public List<SputnikPin> createInputs() {
            return List.of(
                    new SputnikPin("a", "A", PinType.NUMBER, true),
                    new SputnikPin("b", "B", PinType.NUMBER, true)
            );
        }
        public List<SputnikPin> createOutputs() {
            return List.of(new SputnikPin("out", "Result", PinType.NUMBER, false));
        }
        public void execute(SputnikNode node, NodeExecutionContext ctx) {
            double a = ctx.getInputAsDouble("a", 0.0);
            double b = ctx.getInputAsDouble("b", 0.0);
            ctx.setOutput("out", a - b);
        }
    }

    private static class MathMultiplyHandler implements INodeHandler {
        public String getTypeId() { return "math_multiply"; }
        public String getTitle() { return "Multiply"; }
        public String getCategory() { return "Math"; }
        public int getHeaderColor() { return ImColor.rgb(62, 142, 74); }
        public List<SputnikPin> createInputs() {
            return List.of(
                    new SputnikPin("a", "A", PinType.NUMBER, true),
                    new SputnikPin("b", "B", PinType.NUMBER, true)
            );
        }
        public List<SputnikPin> createOutputs() {
            return List.of(new SputnikPin("out", "Result", PinType.NUMBER, false));
        }
        public void execute(SputnikNode node, NodeExecutionContext ctx) {
            double a = ctx.getInputAsDouble("a", 0.0);
            double b = ctx.getInputAsDouble("b", 0.0);
            ctx.setOutput("out", a * b);
        }
    }

    private static class MathDivideHandler implements INodeHandler {
        public String getTypeId() { return "math_divide"; }
        public String getTitle() { return "Divide"; }
        public String getCategory() { return "Math"; }
        public int getHeaderColor() { return ImColor.rgb(62, 142, 74); }
        public List<SputnikPin> createInputs() {
            return List.of(
                    new SputnikPin("a", "A", PinType.NUMBER, true),
                    new SputnikPin("b", "B", PinType.NUMBER, true)
            );
        }
        public List<SputnikPin> createOutputs() {
            return List.of(new SputnikPin("out", "Result", PinType.NUMBER, false));
        }
        public void execute(SputnikNode node, NodeExecutionContext ctx) {
            double a = ctx.getInputAsDouble("a", 0.0);
            double b = ctx.getInputAsDouble("b", 1.0);
            ctx.setOutput("out", Math.abs(b) > 1e-9 ? a / b : 0.0);
        }
    }

    private static class LogicCompareHandler implements INodeHandler {
        public String getTypeId() { return "logic_compare"; }
        public String getTitle() { return "Compare"; }
        public String getCategory() { return "Logic"; }
        public int getHeaderColor() { return ImColor.rgb(180, 110, 45); }
        public List<SputnikPin> createInputs() {
            return List.of(
                    new SputnikPin("a", "A", PinType.NUMBER, true),
                    new SputnikPin("b", "B", PinType.NUMBER, true)
            );
        }
        public List<SputnikPin> createOutputs() {
            return List.of(
                    new SputnikPin("eq", "A == B", PinType.BOOLEAN, false),
                    new SputnikPin("gt", "A > B", PinType.BOOLEAN, false),
                    new SputnikPin("lt", "A < B", PinType.BOOLEAN, false)
            );
        }
        public void execute(SputnikNode node, NodeExecutionContext ctx) {
            double a = ctx.getInputAsDouble("a", 0.0);
            double b = ctx.getInputAsDouble("b", 0.0);
            ctx.setOutput("eq", Math.abs(a - b) < 1e-6);
            ctx.setOutput("gt", a > b);
            ctx.setOutput("lt", a < b);
        }
    }

    private static class LogicAndHandler implements INodeHandler {
        public String getTypeId() { return "logic_and"; }
        public String getTitle() { return "AND"; }
        public String getCategory() { return "Logic"; }
        public int getHeaderColor() { return ImColor.rgb(180, 110, 45); }
        public List<SputnikPin> createInputs() {
            return List.of(
                    new SputnikPin("a", "A", PinType.BOOLEAN, true),
                    new SputnikPin("b", "B", PinType.BOOLEAN, true)
            );
        }
        public List<SputnikPin> createOutputs() {
            return List.of(new SputnikPin("out", "Result", PinType.BOOLEAN, false));
        }
        public void execute(SputnikNode node, NodeExecutionContext ctx) {
            boolean a = ctx.getInputAsBoolean("a", false);
            boolean b = ctx.getInputAsBoolean("b", false);
            ctx.setOutput("out", a && b);
        }
    }

    private static class LogicOrHandler implements INodeHandler {
        public String getTypeId() { return "logic_or"; }
        public String getTitle() { return "OR"; }
        public String getCategory() { return "Logic"; }
        public int getHeaderColor() { return ImColor.rgb(180, 110, 45); }
        public List<SputnikPin> createInputs() {
            return List.of(
                    new SputnikPin("a", "A", PinType.BOOLEAN, true),
                    new SputnikPin("b", "B", PinType.BOOLEAN, true)
            );
        }
        public List<SputnikPin> createOutputs() {
            return List.of(new SputnikPin("out", "Result", PinType.BOOLEAN, false));
        }
        public void execute(SputnikNode node, NodeExecutionContext ctx) {
            boolean a = ctx.getInputAsBoolean("a", false);
            boolean b = ctx.getInputAsBoolean("b", false);
            ctx.setOutput("out", a || b);
        }
    }

    private static class LogicNotHandler implements INodeHandler {
        public String getTypeId() { return "logic_not"; }
        public String getTitle() { return "NOT"; }
        public String getCategory() { return "Logic"; }
        public int getHeaderColor() { return ImColor.rgb(180, 110, 45); }
        public List<SputnikPin> createInputs() {
            return List.of(new SputnikPin("in", "In", PinType.BOOLEAN, true));
        }
        public List<SputnikPin> createOutputs() {
            return List.of(new SputnikPin("out", "Result", PinType.BOOLEAN, false));
        }
        public void execute(SputnikNode node, NodeExecutionContext ctx) {
            boolean in = ctx.getInputAsBoolean("in", false);
            ctx.setOutput("out", !in);
        }
    }

    private static class SensorAltitudeHandler implements INodeHandler {
        public String getTypeId() { return "sensor_altitude"; }
        public String getTitle() { return "Altitude"; }
        public String getCategory() { return "Sensors"; }
        public int getHeaderColor() { return ImColor.rgb(90, 70, 150); }
        public List<SputnikPin> createInputs() { return List.of(); }
        public List<SputnikPin> createOutputs() {
            return List.of(new SputnikPin("alt", "Altitude", PinType.NUMBER, false));
        }
        public void execute(SputnikNode node, NodeExecutionContext ctx) {
            double alt = ctx.getBlockEntity() != null ? ctx.getBlockEntity().getAltitude() : 0.0;
            ctx.setOutput("alt", alt);
        }
    }

    private static class SensorVelocityHandler implements INodeHandler {
        public String getTypeId() { return "sensor_velocity"; }
        public String getTitle() { return "Velocity"; }
        public String getCategory() { return "Sensors"; }
        public int getHeaderColor() { return ImColor.rgb(90, 70, 150); }
        public List<SputnikPin> createInputs() { return List.of(); }
        public List<SputnikPin> createOutputs() {
            return List.of(new SputnikPin("spd", "Speed", PinType.NUMBER, false));
        }
        public void execute(SputnikNode node, NodeExecutionContext ctx) {
            double spd = ctx.getBlockEntity() != null ? ctx.getBlockEntity().getVelocity() : 0.0;
            ctx.setOutput("spd", spd);
        }
    }

    private static class SensorAttitudeHandler implements INodeHandler {
        public String getTypeId() { return "sensor_attitude"; }
        public String getTitle() { return "Attitude"; }
        public String getCategory() { return "Sensors"; }
        public int getHeaderColor() { return ImColor.rgb(90, 70, 150); }
        public List<SputnikPin> createInputs() { return List.of(); }
        public List<SputnikPin> createOutputs() {
            return List.of(
                    new SputnikPin("pitch", "Pitch", PinType.NUMBER, false),
                    new SputnikPin("yaw", "Yaw", PinType.NUMBER, false),
                    new SputnikPin("roll", "Roll", PinType.NUMBER, false),
                    new SputnikPin("x", "Dir X", PinType.NUMBER, false),
                    new SputnikPin("y", "Dir Y", PinType.NUMBER, false),
                    new SputnikPin("z", "Dir Z", PinType.NUMBER, false)
            );
        }
        public void execute(SputnikNode node, NodeExecutionContext ctx) {
            if (ctx.getBlockEntity() != null) {
                var be = ctx.getBlockEntity();
                double pitch = be.getAttitudePitch();
                double yaw = be.getAttitudeYaw();
                double roll = be.getAttitudeRoll();
                var fwd = be.getForwardVector();

                ctx.setOutput("pitch", pitch);
                ctx.setOutput("yaw", yaw);
                ctx.setOutput("roll", roll);
                ctx.setOutput("x", fwd.x);
                ctx.setOutput("dir_x", fwd.x);
                ctx.setOutput("y", fwd.y);
                ctx.setOutput("dir_y", fwd.y);
                ctx.setOutput("z", fwd.z);
                ctx.setOutput("dir_z", fwd.z);
            } else {
                ctx.setOutput("pitch", 0.0);
                ctx.setOutput("yaw", 0.0);
                ctx.setOutput("roll", 0.0);
                ctx.setOutput("x", 0.0);
                ctx.setOutput("dir_x", 0.0);
                ctx.setOutput("y", 0.0);
                ctx.setOutput("dir_y", 0.0);
                ctx.setOutput("z", 1.0);
                ctx.setOutput("dir_z", 1.0);
            }
        }
    }

    private static class DisplayHandler implements INodeHandler {
        private final String typeId;
        private final String title;
        private final int headerColor;

        public DisplayHandler(String typeId, String title, int headerColor) {
            this.typeId = typeId;
            this.title = title;
            this.headerColor = headerColor;
        }

        public String getTypeId() { return typeId; }
        public String getTitle() { return title; }
        public String getCategory() { return "Display"; }
        public int getHeaderColor() { return headerColor; }
        public List<SputnikPin> createInputs() {
            return List.of(new SputnikPin("in", "In", PinType.NUMBER, true));
        }
        public List<SputnikPin> createOutputs() { return List.of(); }
        public void execute(SputnikNode node, NodeExecutionContext ctx) {
            Object val = ctx.getInput("in");
            String str;
            if (val == null) {
                str = "---";
            } else if (val instanceof Double d) {
                str = String.format(Locale.ROOT, "%.3f", d);
            } else if (val instanceof Float f) {
                str = String.format(Locale.ROOT, "%.3f", f);
            } else {
                str = val.toString();
            }
            node.setCustomString(str);
            if (ctx.getBlockEntity() != null && val != null) {
                ctx.getBlockEntity().getDisplayBridge().put("node_" + node.getId(), str);
            }
        }
    }

    private static class SputnikLinkHandler implements INodeHandler {
        public String getTypeId() { return "sputnik_link"; }
        public String getTitle() { return "Sputnik Link"; }
        public String getCategory() { return "Wireless"; }
        public int getHeaderColor() { return ImColor.rgb(185, 115, 35); }
        public List<SputnikPin> createInputs() {
            return List.of(new SputnikPin("out", "Send", PinType.NUMBER, true));
        }
        public List<SputnikPin> createOutputs() {
            return List.of(
                    new SputnikPin("in", "Received", PinType.NUMBER, false),
                    new SputnikPin("active", "Active", PinType.BOOLEAN, false)
            );
        }
        public void execute(SputnikNode node, NodeExecutionContext ctx) {
            int linkId = (int) Math.round(node.getCustomNumber());
            if (linkId <= 0) return;

            int sputnikId = 0;
            long gameTime = 0L;
            if (ctx.getBlockEntity() != null) {
                sputnikId = ctx.getBlockEntity().getSputnikId();
                if (ctx.getBlockEntity().getLevel() != null) {
                    gameTime = ctx.getBlockEntity().getLevel().getGameTime();
                }
            }

            Object outVal = ctx.getInput("out");
            int sendSignal = 0;
            if (outVal != null) {
                if (outVal instanceof Number n) {
                    sendSignal = n.intValue();
                } else if (outVal instanceof Boolean b) {
                    sendSignal = b ? 15 : 0;
                }
            }
            dev.devce.rocketnautics.content.blocks.sputnik_link.SputnikLinkManager.setTransmittedSignal(sputnikId, linkId, sendSignal, gameTime);

            int received = dev.devce.rocketnautics.content.blocks.sputnik_link.SputnikLinkManager.getReceivedSignal(linkId);
            ctx.setOutput("in", (double) received);
            ctx.setOutput("active", received > 0);
        }
    }

    private static class SatelliteCommsHandler implements INodeHandler {
        public String getTypeId() { return "satellite_comms"; }
        public String getTitle() { return "Satellite Comms"; }
        public String getCategory() { return "Wireless"; }
        public int getHeaderColor() { return ImColor.rgb(45, 115, 185); }

        public List<SputnikPin> createInputs() {
            return List.of(
                    new SputnikPin("ch", "Channel", PinType.NUMBER, true),
                    new SputnikPin("out", "Send", PinType.NUMBER, true)
            );
        }

        public List<SputnikPin> createOutputs() {
            return List.of(
                    new SputnikPin("in", "Received", PinType.NUMBER, false),
                    new SputnikPin("from", "From ID", PinType.NUMBER, false),
                    new SputnikPin("active", "Active", PinType.BOOLEAN, false)
            );
        }

        public void execute(SputnikNode node, NodeExecutionContext ctx) {
            int channel = (int) Math.round(node.getCustomNumber());
            Object chInput = ctx.getInput("ch");
            if (chInput instanceof Number n) {
                channel = n.intValue();
            }
            if (channel <= 0) return;

            int sputnikId = 0;
            long gameTime = 0L;
            if (ctx.getBlockEntity() != null) {
                sputnikId = ctx.getBlockEntity().getSputnikId();
                if (ctx.getBlockEntity().getLevel() != null) {
                    gameTime = ctx.getBlockEntity().getLevel().getGameTime();
                }
            }

            Object outVal = ctx.getInput("out");
            if (outVal != null) {
                double sendVal = 0.0;
                if (outVal instanceof Number n) {
                    sendVal = n.doubleValue();
                } else if (outVal instanceof Boolean b) {
                    sendVal = b ? 1.0 : 0.0;
                }
                dev.devce.rocketnautics.content.sputnik.comms.SputnikCommsManager.send(sputnikId, channel, sendVal, gameTime);
            }

            var packet = dev.devce.rocketnautics.content.sputnik.comms.SputnikCommsManager.receive(sputnikId, channel, gameTime);
            if (packet != null) {
                ctx.setOutput("in", packet.data);
                ctx.setOutput("from", (double) packet.senderSputnikId);
                ctx.setOutput("active", true);
            } else {
                ctx.setOutput("in", 0.0);
                ctx.setOutput("from", 0.0);
                ctx.setOutput("active", false);
            }
        }
    }

    private static class EngineIgnitionHandler implements INodeHandler {
        public String getTypeId() { return "engine_ignition"; }
        public String getTitle() { return "Ignition Control"; }
        public String getCategory() { return "Actuators"; }
        public int getHeaderColor() { return ImColor.rgb(205, 75, 45); }

        public List<SputnikPin> createInputs() {
            return List.of(
                    new SputnikPin("id", "Engine ID", PinType.NUMBER, true),
                    new SputnikPin("ignite", "Ignite", PinType.BOOLEAN, true)
            );
        }

        public List<SputnikPin> createOutputs() {
            return List.of(
                    new SputnikPin("active", "Active", PinType.BOOLEAN, false)
            );
        }

        public void execute(SputnikNode node, NodeExecutionContext ctx) {
            int id = (int) Math.round(node.getCustomNumber());
            Object idIn = ctx.getInput("id");
            if (idIn instanceof Number n) {
                id = n.intValue();
            }
            if (id < 0 || ctx.getBlockEntity() == null || ctx.getBlockEntity().getLevel() == null) {
                ctx.setOutput("active", false);
                return;
            }

            IPeripheral peripheral = PeripheralRegistry.getPeripheralById(ctx.getBlockEntity().getLevel(), id);
            if (peripheral != null) {
                boolean ignite = ctx.getBooleanInput("ignite", false);
                peripheral.writeValue("ignition", ignite ? 1.0 : 0.0);
                ctx.setOutput("active", peripheral.readValue("active") > 0.5);
            } else {
                ctx.setOutput("active", false);
            }
        }
    }

    private static class EngineThrustHandler implements INodeHandler {
        public String getTypeId() { return "engine_thrust"; }
        public String getTitle() { return "Thrust Control"; }
        public String getCategory() { return "Actuators"; }
        public int getHeaderColor() { return ImColor.rgb(215, 135, 30); }

        public List<SputnikPin> createInputs() {
            return List.of(
                    new SputnikPin("id", "Engine ID", PinType.NUMBER, true),
                    new SputnikPin("thrust", "Throttle", PinType.NUMBER, true)
            );
        }

        public List<SputnikPin> createOutputs() {
            return List.of(
                    new SputnikPin("current", "Current Thrust", PinType.NUMBER, false)
            );
        }

        public void execute(SputnikNode node, NodeExecutionContext ctx) {
            int id = (int) Math.round(node.getCustomNumber());
            Object idIn = ctx.getInput("id");
            if (idIn instanceof Number n) {
                id = n.intValue();
            }
            if (id < 0 || ctx.getBlockEntity() == null || ctx.getBlockEntity().getLevel() == null) {
                ctx.setOutput("current", 0.0);
                return;
            }

            IPeripheral peripheral = PeripheralRegistry.getPeripheralById(ctx.getBlockEntity().getLevel(), id);
            if (peripheral != null) {
                double throttle = Math.max(0.0, Math.min(1.0, ctx.getNumberInput("thrust", 0.0)));
                peripheral.writeValue("throttle", throttle);
                ctx.setOutput("current", peripheral.readValue("thrust"));
            } else {
                ctx.setOutput("current", 0.0);
            }
        }
    }

    private static class EngineVectorHandler implements INodeHandler {
        public String getTypeId() { return "engine_vector"; }
        public String getTitle() { return "Vector Control"; }
        public String getCategory() { return "Actuators"; }
        public int getHeaderColor() { return ImColor.rgb(45, 125, 215); }

        public List<SputnikPin> createInputs() {
            return List.of(
                    new SputnikPin("id", "Engine ID", PinType.NUMBER, true),
                    new SputnikPin("pitch", "Pitch", PinType.NUMBER, true),
                    new SputnikPin("yaw", "Yaw", PinType.NUMBER, true)
            );
        }

        public List<SputnikPin> createOutputs() {
            return List.of(
                    new SputnikPin("cur_pitch", "Cur Pitch", PinType.NUMBER, false),
                    new SputnikPin("cur_yaw", "Cur Yaw", PinType.NUMBER, false)
            );
        }

        public void execute(SputnikNode node, NodeExecutionContext ctx) {
            int id = (int) Math.round(node.getCustomNumber());
            Object idIn = ctx.getInput("id");
            if (idIn instanceof Number n) {
                id = n.intValue();
            }
            if (id < 0 || ctx.getBlockEntity() == null || ctx.getBlockEntity().getLevel() == null) {
                ctx.setOutput("cur_pitch", 0.0);
                ctx.setOutput("cur_yaw", 0.0);
                return;
            }

            IPeripheral peripheral = PeripheralRegistry.getPeripheralById(ctx.getBlockEntity().getLevel(), id);
            if (peripheral != null) {
                double pitch = Math.max(-1.0, Math.min(1.0, ctx.getNumberInput("pitch", 0.0)));
                double yaw = Math.max(-1.0, Math.min(1.0, ctx.getNumberInput("yaw", 0.0)));
                peripheral.writeValues("gimbal", pitch, yaw);
                ctx.setOutput("cur_pitch", peripheral.readValue("pitch"));
                ctx.setOutput("cur_yaw", peripheral.readValue("yaw"));
            } else {
                ctx.setOutput("cur_pitch", 0.0);
                ctx.setOutput("cur_yaw", 0.0);
            }
        }
    }

    private static class GyrodyneControlHandler implements INodeHandler {
        public String getTypeId() { return "gyrodyne_control"; }
        public String getTitle() { return "Gyrodyne Control"; }
        public String getCategory() { return "Actuators"; }
        public int getHeaderColor() { return ImColor.rgb(125, 77, 35); }

        public List<SputnikPin> createInputs() {
            return List.of(
                    new SputnikPin("id", "Gyro ID", PinType.NUMBER, true),
                    new SputnikPin("mode", "Mode (0-10)", PinType.NUMBER, true),
                    new SputnikPin("active", "Enable / SAS", PinType.BOOLEAN, true)
            );
        }

        public List<SputnikPin> createOutputs() {
            return List.of(
                    new SputnikPin("cur_mode", "Cur Mode", PinType.NUMBER, false),
                    new SputnikPin("mode_name", "Mode Name", PinType.STRING, false),
                    new SputnikPin("active", "Is Active", PinType.BOOLEAN, false),
                    new SputnikPin("rotor_speed", "Rotor Speed", PinType.NUMBER, false),
                    new SputnikPin("tilt_x", "Tilt X", PinType.NUMBER, false),
                    new SputnikPin("tilt_z", "Tilt Z", PinType.NUMBER, false)
            );
        }

        public void execute(SputnikNode node, NodeExecutionContext ctx) {
            int id = (int) Math.round(node.getCustomNumber());
            Object idIn = ctx.getInput("id");
            if (idIn instanceof Number n) {
                id = n.intValue();
            }
            if (id < 0 || ctx.getBlockEntity() == null || ctx.getBlockEntity().getLevel() == null) {
                ctx.setOutput("cur_mode", 0.0);
                ctx.setOutput("mode_name", "OFF");
                ctx.setOutput("active", false);
                ctx.setOutput("rotor_speed", 0.0);
                ctx.setOutput("tilt_x", 0.0);
                ctx.setOutput("tilt_z", 0.0);
                return;
            }

            IPeripheral peripheral = PeripheralRegistry.getPeripheralById(ctx.getBlockEntity().getLevel(), id);
            if (peripheral != null) {
                Object modeIn = ctx.getInput("mode");
                if (modeIn instanceof Number num) {
                    int m = num.intValue();
                    if (m >= 0 && m < GyrodyneMode.values().length) {
                        peripheral.writeValue("mode", m);
                    }
                } else if (modeIn instanceof String str && !str.isEmpty()) {
                    for (GyrodyneMode gm : GyrodyneMode.values()) {
                        if (gm.getSerializedName().equalsIgnoreCase(str) || gm.name().equalsIgnoreCase(str)) {
                            peripheral.writeValue("mode", gm.ordinal());
                            break;
                        }
                    }
                }

                Object actIn = ctx.getInput("active");
                if (actIn instanceof Boolean b) {
                    if (!b) {
                        peripheral.writeValue("off", 1.0);
                    } else if (modeIn == null && peripheral.readValue("mode") == 0.0) {
                        peripheral.writeValue("sas", 1.0);
                    }
                } else if (actIn instanceof Number n) {
                    if (n.doubleValue() <= 0.0) {
                        peripheral.writeValue("off", 1.0);
                    } else if (modeIn == null && peripheral.readValue("mode") == 0.0) {
                        peripheral.writeValue("sas", 1.0);
                    }
                }

                int curModeOrd = (int) Math.round(peripheral.readValue("mode"));
                GyrodyneMode curMode = GyrodyneMode.fromIndex(curModeOrd);
                boolean isActive = peripheral.readValue("active") > 0.5;

                ctx.setOutput("cur_mode", (double) curModeOrd);
                ctx.setOutput("mode_name", curMode.name());
                ctx.setOutput("active", isActive);
                ctx.setOutput("rotor_speed", peripheral.readValue("rotorspeed"));
                ctx.setOutput("tilt_x", peripheral.readValue("tilt_x"));
                ctx.setOutput("tilt_z", peripheral.readValue("tilt_z"));
            } else {
                ctx.setOutput("cur_mode", 0.0);
                ctx.setOutput("mode_name", "OFF");
                ctx.setOutput("active", false);
                ctx.setOutput("rotor_speed", 0.0);
                ctx.setOutput("tilt_x", 0.0);
                ctx.setOutput("tilt_z", 0.0);
            }
        }
    }
}

package com.wrights.job;

import java.util.Map;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.server.MinecraftServer;
import com.wrights.schematic.Schematic;
import com.wrights.schematic.SchematicLibrary;

/** Builds the readable titles for jobs: the live HUD title and the stored Jobs card and history title. */
public final class JobTitles {
    private JobTitles() {
    }

    static Component jobTitle(MinecraftServer server, SchematicLibrary library, BuildJob job, String taskKey) {
        Schematic schematic = job.schematicId() == null || library == null ? null : library.schematic(job.schematicId()).orElse(null);
        boolean mirror = job.mining() || (schematic != null && schematic.ephemeral() && com.wrights.blueprint.Mirror.marksBlocks(schematic.name()));
        String mode;
        if (job.exchangeJob()) {
            mode = "hud.wrights.tool_exchange";
        } else if (job.kind() == BuildJob.Kind.SHAPE) {
            mode = "hud.wrights.tool_shape";
        } else if (mirror) {
            mode = "hud.wrights.tool_mirror";
        } else if (job.kind() == BuildJob.Kind.COPY) {
            mode = "hud.wrights.tool_new";
        } else {
            mode = "hud.wrights.tool_build";
        }
        String subject = subjectName(job, schematic);
        boolean blockPlace = mirror && subject.isEmpty() && !job.mining();
        String task = taskKey;
        if (task == null) {
            if (job.mining()) {
                task = "hud.wrights.task_mine";
            } else if (job.kind() == BuildJob.Kind.COPY) {
                task = "hud.wrights.task_save";
            } else if (blockPlace) {
                task = "hud.wrights.task_place";
            } else {
                task = "hud.wrights.task_build";
            }
        }
        MutableComponent title = Component.translatable(mode).append(" - ").append(Component.translatable(task));
        if (job.mining()) {
            if (job.mineAny()) {
                title.append(" - ").append(Component.translatable("menu.wrights.mirror_mine_any"));
            }
            if (job.mineVoid()) {
                title.append(" - ").append(Component.translatable("menu.wrights.mirror_mine_void"));
            }
            if (JobManager.silkTouch(server, job.mineTool())) {
                title.append(" - ").append(Component.translatable("menu.wrights.mirror_mine_silk"));
            }
        } else if (job.kind() == BuildJob.Kind.COPY) {
            if (job.copyFluids()) {
                title.append(" - ").append(Component.translatable("screen.wrights.copy_fluids"));
            }
            if (job.copyNbtAll()) {
                title.append(" - ").append(Component.translatable("screen.wrights.copy_nbt_all"));
            } else if (job.copyNbtSettings()) {
                title.append(" - ").append(Component.translatable("screen.wrights.copy_nbt_settings"));
            } else if (job.copyNbtBlocks()) {
                title.append(" - ").append(Component.translatable("screen.wrights.copy_nbt_blocks"));
            }
        } else if (blockPlace) {
            if (job.voidBlocks()) {
                title.append(" - ").append(Component.translatable("menu.wrights.mirror_replace"));
            }
        } else if (!"hud.wrights.task_undo".equals(task) || job.voidBlocks() || job.voidFluids() || job.voidNbt()) {
            if (job.voidBlocks()) {
                title.append(" - ").append(Component.translatable("screen.wrights.void_blocks"));
            }
            if (job.voidFluids()) {
                title.append(" - ").append(Component.translatable("screen.wrights.void_fluids"));
            }
            if (job.voidNbt()) {
                title.append(" - ").append(Component.translatable("screen.wrights.void_nbt"));
            }
        }
        if (!subject.isEmpty()) {
            title.append(" - ").append(Component.literal(subject));
        }
        return title;
    }

    private static String subjectName(BuildJob job, Schematic schematic) {
        String name = job.blueprintName();
        if (name != null) {
            for (String prefix : new String[] {"Shape - ", "Exchange - ", "Blueprint - "}) {
                if (name.startsWith(prefix)) {
                    name = name.substring(prefix.length()).trim();
                    break;
                }
            }
        }
        if (name != null && (name.equals("Mirror - Mine") || name.equals("Build") || name.equals("Saving")
                || name.equals("Blueprint") || name.equals("Exchange") || name.equals("Shape"))) {
            name = null;
        }
        if ((name == null || name.isBlank()) && schematic != null) {
            name = schematic.name();
            if (name != null && name.startsWith("Blueprint - ")) {
                name = name.substring("Blueprint - ".length()).trim();
            }
        }
        if (name == null || name.isBlank()) {
            return "";
        }
        name = stripMirrorSuffix(name);
        if (name.equals("Mirror")) {
            return "";
        }
        return name;
    }

    /** Jobs card / stored title: {@code Shape - Ring}, {@code Exchange - Wall}, {@code Blueprint - Dock}. */
    public static String jobCardTitle(BuildJob job, SchematicLibrary library) {
        if (job == null) {
            return "Build";
        }
        if (job.kind() == BuildJob.Kind.SHAPE) {
            String current = job.blueprintName();
            if (current != null && (current.startsWith("Shape - ") || current.startsWith("Exchange - ") || current.startsWith("Place - "))) {
                return clipJobTitle(current);
            }
            String titled = shapeJobTitle(
                    job.exchangeJob(),
                    job.shapeKind(),
                    job.shapeSize(),
                    job.shapeHeight(),
                    job.shapeThickness(),
                    job.shapeAxis());
            if (job.placeTool() && !job.exchangeJob() && titled.startsWith("Shape - ")) {
                titled = "Place - " + titled.substring("Shape - ".length());
            }
            return clipJobTitle(titled);
        }
        if (job.kind() == BuildJob.Kind.COPY) {
            String current = job.blueprintName();
            if (current != null && current.startsWith("Blueprint - ")) {
                return clipJobTitle(current);
            }
            String bare = current == null || current.isBlank() ? "Saving" : current;
            return clipJobTitle(blueprintJobTitle(bare));
        }
        // PASTE — blueprints, mirror copies, and Place path batches.
        String current = job.blueprintName();
        if (current != null && !current.isBlank()) {
            if (current.startsWith("Blueprint - ") || current.startsWith("Mirror") || current.startsWith("Place - ")) {
                return clipJobTitle(current);
            }
            if (current.equals("Build") || current.equals("Saving") || current.equals("Blueprint")) {
                current = null;
            }
        }
        if ((current == null || current.isBlank()) && job.schematicId() != null && library != null) {
            current = library.schematic(job.schematicId()).map(Schematic::name).orElse(null);
        }
        if (current == null || current.isBlank()) {
            return "Blueprint - Build";
        }
        if (current.startsWith("Mirror")) {
            return clipJobTitle(current);
        }
        return clipJobTitle(blueprintJobTitle(current));
    }

    public static String shapeJobTitle(boolean exchange, int kindId, int size, int height, int thickness, int axis) {
        String mode = exchange ? "Exchange" : "Shape";
        String detail = exchange
                ? exchangeFormLabel(kindId, size, height, thickness, axis)
                : shapeKindLabel(com.wrights.blueprint.Shapes.Kind.byId(kindId));
        return mode + " - " + detail;
    }

    public static String blueprintJobTitle(String blueprintName) {
        String bare = blueprintName == null ? "" : blueprintName.trim();
        if (bare.startsWith("Blueprint - ")) {
            return bare;
        }
        if (bare.isEmpty()) {
            bare = "Build";
        }
        return "Blueprint - " + bare;
    }

    private static String exchangeFormLabel(int kindId, int size, int height, int thickness, int axis) {
        com.wrights.blueprint.Shapes.Kind kind = com.wrights.blueprint.Shapes.Kind.byId(kindId);
        return switch (kind) {
            case CIRCLE -> "Circle";
            case BOX -> {
                if (Math.max(1, size) <= 1 && Math.max(1, thickness) <= 1) {
                    yield "Pillar";
                }
                if (Math.max(1, height) <= 1) {
                    yield "Beam";
                }
                yield "Box";
            }
            case SQUARE, RECTANGLE -> com.wrights.blueprint.Shapes.clampPlane(axis) == com.wrights.blueprint.Shapes.AXIS_Y
                    ? "Floor"
                    : "Wall";
            default -> shapeKindLabel(kind);
        };
    }

    private static String shapeKindLabel(com.wrights.blueprint.Shapes.Kind kind) {
        if (kind == null) {
            return "Shape";
        }
        String raw = kind.name().toLowerCase(java.util.Locale.ROOT).replace('_', ' ');
        return Character.toUpperCase(raw.charAt(0)) + raw.substring(1);
    }

    private static String clipJobTitle(String title) {
        if (title == null || title.isBlank()) {
            return "Build";
        }
        return title.length() > 48 ? title.substring(0, 48) : title;
    }

    private static String stripMirrorSuffix(String name) {
        if (name.endsWith(" · mirror")) {
            return name.substring(0, name.length() - " · mirror".length()).trim();
        }
        int cut = name.lastIndexOf(" · ");
        if (cut <= 0) {
            return name;
        }
        String tail = name.substring(cut + 3);
        if (tail.isEmpty()) {
            return name;
        }
        for (int i = 0; i < tail.length(); i++) {
            if (!Character.isDigit(tail.charAt(i))) {
                return name;
            }
        }
        return name.substring(0, cut).trim();
    }
}

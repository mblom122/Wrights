package com.wrights.client;

/**
 * Help topics for the Help menu. Content lives in lang under {@code help.wrights.*}.
 * Line kinds: {@code H} heading, {@code P} body, {@code K} key chips (pipe-separated), {@code T} tip.
 */
public final class HelpTopics {
    public record Topic(String id, String navKey) {
    }

    /** One drawn line. {@code kind} is H, P, K, or T. */
    public record Line(String kind, String text) {
    }

    public static final Topic[] TOPICS = {
            new Topic("start", "help.wrights.nav.start"),
            new Topic("modes", "help.wrights.nav.modes"),
            new Topic("new", "help.wrights.nav.new"),
            new Topic("build", "help.wrights.nav.build"),
            new Topic("mirror", "help.wrights.nav.mirror"),
            new Topic("place", "help.wrights.nav.place"),
            new Topic("shape", "help.wrights.nav.shape"),
            new Topic("exchange", "help.wrights.nav.exchange"),
            new Topic("bind", "help.wrights.nav.bind"),
            new Topic("jobs", "help.wrights.nav.jobs"),
            new Topic("manage", "help.wrights.nav.manage"),
            new Topic("info", "help.wrights.nav.info"),
            new Topic("link", "help.wrights.nav.link"),
            new Topic("library", "help.wrights.nav.library"),
            new Topic("supply", "help.wrights.nav.supply"),
            new Topic("keys", "help.wrights.nav.keys")
    };

    private HelpTopics() {
    }

    public static Line[] lines(String id) {
        return switch (id) {
            case "start" -> new Line[] {
                    line("H", "help.wrights.start.h1"),
                    line("P", "help.wrights.start.p1"),
                    line("P", "help.wrights.start.p2"),
                    line("H", "help.wrights.start.h2"),
                    line("P", "help.wrights.start.p3"),
                    line("K", "B|Ctrl+Shift+Scroll|Shift+Scroll"),
                    line("T", "help.wrights.start.tip")
            };
            case "modes" -> new Line[] {
                    line("H", "help.wrights.modes.h1"),
                    line("P", "help.wrights.modes.p1"),
                    line("H", "help.wrights.modes.h2"),
                    line("P", "help.wrights.modes.p_new"),
                    line("P", "help.wrights.modes.p_build"),
                    line("P", "help.wrights.modes.p_jobs"),
                    line("P", "help.wrights.modes.p_manage"),
                    line("P", "help.wrights.modes.p_info"),
                    line("P", "help.wrights.modes.p_link"),
                    line("T", "help.wrights.modes.tip")
            };
            case "new" -> new Line[] {
                    line("H", "help.wrights.new.h1"),
                    line("P", "help.wrights.new.p1"),
                    line("H", "help.wrights.new.h2"),
                    line("P", "help.wrights.new.p_corners"),
                    line("P", "help.wrights.new.p_box"),
                    line("P", "help.wrights.new.p_move"),
                    line("K", "Shift+Scroll|Right-click|Shift+Right-click|Ctrl+Scroll"),
                    line("H", "help.wrights.new.h3"),
                    line("P", "help.wrights.new.p2"),
                    line("T", "help.wrights.new.tip")
            };
            case "build" -> new Line[] {
                    line("H", "help.wrights.build.h1"),
                    line("P", "help.wrights.build.p1"),
                    line("H", "help.wrights.build.h2"),
                    line("P", "help.wrights.build.p_tools"),
                    line("P", "help.wrights.build.p_place"),
                    line("P", "help.wrights.build.p_menu"),
                    line("K", "Right-click|Ctrl+Right-click|Ctrl+Shift+Right-click|Ctrl+Scroll|Alt+Right-click"),
                    line("H", "help.wrights.build.h3"),
                    line("P", "help.wrights.build.p2"),
                    line("T", "help.wrights.build.tip")
            };
            case "mirror" -> new Line[] {
                    line("H", "help.wrights.mirror.h1"),
                    line("P", "help.wrights.mirror.p1"),
                    line("H", "help.wrights.mirror.h2"),
                    line("P", "help.wrights.mirror.p_blocks"),
                    line("P", "help.wrights.mirror.p_plans"),
                    line("P", "help.wrights.mirror.p_copies"),
                    line("P", "help.wrights.mirror.p_mine"),
                    line("K", "Ctrl+Right-click|Right-click|Ctrl+Shift+Right-click"),
                    line("T", "help.wrights.mirror.tip")
            };
            case "shape" -> new Line[] {
                    line("H", "help.wrights.shape.h1"),
                    line("P", "help.wrights.shape.p1"),
                    line("H", "help.wrights.shape.h2"),
                    line("P", "help.wrights.shape.p_kinds"),
                    line("P", "help.wrights.shape.p_mats"),
                    line("P", "help.wrights.shape.p_aim"),
                    line("P", "help.wrights.shape.p_void"),
                    line("K", "Right-click|Shift+Right-click|Ctrl+Right-click|Ctrl+Shift+Right-click|Middle-click|Ctrl+Scroll"),
                    line("T", "help.wrights.shape.tip")
            };
            case "place" -> new Line[] {
                    line("H", "help.wrights.place.h1"),
                    line("P", "help.wrights.place.p1"),
                    line("H", "help.wrights.place.h2"),
                    line("P", "help.wrights.place.p2"),
                    line("K", "Right-click|Shift+Right-click|Ctrl+Shift+Right-click|Middle-click|Ctrl+Scroll"),
                    line("T", "help.wrights.place.tip")
            };
            case "exchange" -> new Line[] {
                    line("H", "help.wrights.exchange.h1"),
                    line("P", "help.wrights.exchange.p1"),
                    line("H", "help.wrights.exchange.h2"),
                    line("P", "help.wrights.exchange.p2"),
                    line("K", "Right-click|Middle-click|Ctrl+Right-click|Ctrl+Shift+Right-click|Ctrl+Scroll"),
                    line("T", "help.wrights.exchange.tip")
            };
            case "bind" -> new Line[] {
                    line("H", "help.wrights.bind.h1"),
                    line("P", "help.wrights.bind.p1"),
                    line("H", "help.wrights.bind.h2"),
                    line("P", "help.wrights.bind.p2"),
                    line("K", "Right-click|Shift+Right-click|Ctrl+Shift+Right-click"),
                    line("T", "help.wrights.bind.tip")
            };
            case "jobs" -> new Line[] {
                    line("H", "help.wrights.jobs.h1"),
                    line("P", "help.wrights.jobs.p1"),
                    line("H", "help.wrights.jobs.h2"),
                    line("P", "help.wrights.jobs.p2"),
                    line("T", "help.wrights.jobs.tip")
            };
            case "manage" -> new Line[] {
                    line("H", "help.wrights.manage.h1"),
                    line("P", "help.wrights.manage.p1"),
                    line("H", "help.wrights.manage.h2"),
                    line("P", "help.wrights.manage.p_personal"),
                    line("P", "help.wrights.manage.p_ports"),
                    line("P", "help.wrights.manage.p_upgrades"),
                    line("T", "help.wrights.manage.tip")
            };
            case "info" -> new Line[] {
                    line("H", "help.wrights.info.h1"),
                    line("P", "help.wrights.info.p1"),
                    line("H", "help.wrights.info.h2"),
                    line("P", "help.wrights.info.p2"),
                    line("T", "help.wrights.info.tip")
            };
            case "link" -> new Line[] {
                    line("H", "help.wrights.link.h1"),
                    line("P", "help.wrights.link.p1"),
                    line("H", "help.wrights.link.h2"),
                    line("P", "help.wrights.link.p_ae2"),
                    line("P", "help.wrights.link.p_rs"),
                    line("T", "help.wrights.link.tip")
            };
            case "library" -> new Line[] {
                    line("H", "help.wrights.library.h1"),
                    line("P", "help.wrights.library.p1"),
                    line("H", "help.wrights.library.h2"),
                    line("P", "help.wrights.library.p_world"),
                    line("P", "help.wrights.library.p_global"),
                    line("P", "help.wrights.library.p_transfer"),
                    line("T", "help.wrights.library.tip")
            };
            case "supply" -> new Line[] {
                    line("H", "help.wrights.supply.h1"),
                    line("P", "help.wrights.supply.p1"),
                    line("H", "help.wrights.supply.h2"),
                    line("P", "help.wrights.supply.p_player"),
                    line("P", "help.wrights.supply.p_chest"),
                    line("P", "help.wrights.supply.p_digital"),
                    line("P", "help.wrights.supply.p_materials"),
                    line("T", "help.wrights.supply.tip")
            };
            case "keys" -> new Line[] {
                    line("H", "help.wrights.keys.h1"),
                    line("P", "help.wrights.keys.p1"),
                    line("K", "B|Ctrl+Shift+Scroll|Shift+Scroll|Ctrl+Scroll"),
                    line("H", "help.wrights.keys.h2"),
                    line("P", "help.wrights.keys.p2"),
                    line("K", "Right-click|Ctrl+Right-click|Shift+Right-click|Ctrl+Shift+Right-click|Alt+Right-click"),
                    line("H", "help.wrights.keys.h3"),
                    line("P", "help.wrights.keys.p3"),
                    line("K", "1 · Ctrl 5 · Shift 10 · Alt 50 · Ctrl+Shift 100"),
                    line("T", "help.wrights.keys.tip")
            };
            default -> new Line[] {
                    line("P", "help.wrights.missing")
            };
        };
    }

    private static Line line(String kind, String text) {
        return new Line(kind, text);
    }
}

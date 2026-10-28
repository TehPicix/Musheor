package musheor.commands;

import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import com.mojang.brigadier.context.CommandContext;
import com.mojang.brigadier.suggestion.Suggestions;
import com.mojang.brigadier.suggestion.SuggestionsBuilder;
import java.lang.reflect.Method;
import java.lang.reflect.Modifier;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.stream.Stream;
import meteordevelopment.meteorclient.MeteorClient;
import meteordevelopment.meteorclient.commands.Command;
import net.fabricmc.loader.api.FabricLoader;
import net.fabricmc.loader.api.ModContainer;
import net.minecraft.client.MinecraftClient;
import net.minecraft.command.CommandSource;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.registry.Registries;
import net.minecraft.util.Identifier;

public class Execute extends Command {
    private static final Map<String, Class<?>> classesByName = new HashMap<>();
    private static final Map<String, List<String>> methodsByClass = new HashMap<>();

    public Execute() {
        super("execute", "Run test methods dynamically and auto-suggest available ones.");
        this.scanPackage("musheor");
    }

    @Override
    public void build(LiteralArgumentBuilder<CommandSource> builder) {
        builder.then(argument("call", StringArgumentType.greedyString()).suggests(this::suggestCalls).executes(this::executeCall));
    }

    private int executeCall(CommandContext<CommandSource> context) {
        String input = StringArgumentType.getString(context, "call");

        try {
            String[] tokens = input.split(" ");
            if (tokens.length == 0) {
                this.error("Usage: .execute ClassName.methodName [args...]", new Object[0]);
                return 1;
            }

            String[] parts = tokens[0].split("\\.");
            if (parts.length != 2) {
                this.error("Use format: ClassName.methodName", new Object[0]);
                return 1;
            }

            String className = parts[0];
            String methodName = parts[1];
            Class<?> cls = classesByName.get(className);
            if (cls == null) {
                this.error("Unknown class: " + className, new Object[0]);
                return 1;
            }

            String[] args = Arrays.copyOfRange(tokens, 1, tokens.length);
            Method target = this.findMethod(cls, methodName, args.length);
            if (target == null) {
                this.error("No suitable method found for " + methodName + " with " + args.length + " args.", new Object[0]);
                return 1;
            }

            Object[] converted = this.convertArgs(target.getParameterTypes(), args);
            Object instance = Modifier.isStatic(target.getModifiers()) ? null : cls.getDeclaredConstructor().newInstance();
            target.setAccessible(true);
            Object result = target.invoke(instance, converted);
            this.info("Executed " + tokens[0] + (result != null ? " -> " + result : ""), new Object[0]);
        } catch (Exception var13) {
            this.error("Error: " + var13.getClass().getSimpleName() + " - " + var13.getMessage(), new Object[0]);
            var13.printStackTrace();
        }

        return 1;
    }

    private Method findMethod(Class<?> cls, String name, int argCount) {
        for (Method m : cls.getDeclaredMethods()) {
            if (m.getName().equals(name) && m.getParameterCount() == argCount) {
                return m;
            }
        }

        return null;
    }

    private Object[] convertArgs(Class<?>[] types, String[] args) {
        Object[] converted = new Object[args.length];

        for (int i = 0; i < args.length; i++) {
            converted[i] = this.convertArg(args[i], types[i]);
        }

        return converted;
    }

    private Object convertArg(String s, Class<?> type) {
        try {
            if (type == String.class) {
                return s;
            } else if (type == int.class || type == Integer.class) {
                return Integer.parseInt(s);
            } else if (type == double.class || type == Double.class) {
                return Double.parseDouble(s);
            } else if (type == float.class || type == Float.class) {
                return Float.parseFloat(s);
            } else if (type == boolean.class || type == Boolean.class) {
                return Boolean.parseBoolean(s);
            } else if (type == long.class || type == Long.class) {
                return Long.parseLong(s);
            } else if (type == Identifier.class) {
                return Identifier.of(s);
            } else if (type == Item.class) {
                return Registries.ITEM.get(Identifier.of(s));
            } else if (type == ItemStack.class) {
                String[] parts = s.split(":");
                int count = 1;
                Item item;
                if (parts.length >= 2 && parts[0].equalsIgnoreCase("minecraft")) {
                    item = Registries.ITEM.get(Identifier.of(parts[0], parts[1]));
                    if (parts.length == 3) {
                        try {
                            count = Integer.parseInt(parts[2]);
                        } catch (Exception var7) {
                        }
                    }
                } else if (parts.length == 1) {
                    item = Registries.ITEM.get(Identifier.of("minecraft", parts[0]));
                } else {
                    item = Registries.ITEM.get(Identifier.of(s));
                }

                return item == null ? null : new ItemStack(item, count);
            } else {
                if (type == PlayerEntity.class) {
                    MinecraftClient mc = MeteorClient.mc;
                    if (s.equalsIgnoreCase("@p") || s.equalsIgnoreCase("self")) {
                        return mc.player;
                    }
                }

                return s;
            }
        } catch (Exception var8) {
            System.err.println("[Execute] Failed to convert '" + s + "' to " + type.getSimpleName() + ": " + var8);
            return null;
        }
    }

    private CompletableFuture<Suggestions> suggestCalls(CommandContext<CommandSource> context, SuggestionsBuilder builder) {
        String input = builder.getRemaining();
        if (!input.contains(".")) {
            classesByName.keySet().stream().filter(name -> name.startsWith(input)).forEach(builder::suggest);
        } else {
            String className = input.substring(0, input.indexOf(46));
            List<String> methods = methodsByClass.get(className);
            if (methods != null) {
                String prefix = className + ".";
                methods.stream().map(m -> prefix + m).filter(name -> name.startsWith(input)).forEach(builder::suggest);
            }
        }

        return builder.buildFuture();
    }

    private void scanPackage(String basePackage) {
        ModContainer container = (ModContainer)FabricLoader.getInstance().getModContainer("musheor").orElse(null);
        if (container == null) {
            System.err.println("[Execute] Could not find musheor mod container.");
        } else {
            String pkgPath = basePackage.replace('.', '/');

            for (Path root : container.getRootPaths()) {
                Path pkgDir = root.resolve(pkgPath);
                if (Files.exists(pkgDir)) {
                    try (Stream<Path> stream = Files.walk(pkgDir)) {
                        stream.filter(p -> p.getFileName().toString().endsWith(".class")).forEach(p -> {
                            String rel = root.relativize(p).toString().replace('/', '.').replace('\\', '.').replace(".class", "");
                            this.registerClass(rel);
                        });
                    } catch (Exception var12) {
                        System.err.println("[Execute] Scan failed on " + root + ": " + var12);
                    }
                }
            }
        }
    }

    private void registerClass(String fullName) {
        try {
            Class<?> cls = Class.forName(fullName);
            String simple = cls.getSimpleName();
            classesByName.put(simple, cls);
            List<String> names = new ArrayList<>();

            for (Method m : cls.getDeclaredMethods()) {
                if (!m.isSynthetic()) {
                    names.add(m.getName());
                }
            }

            methodsByClass.put(simple, names);
        } catch (Throwable var9) {
        }
    }
}

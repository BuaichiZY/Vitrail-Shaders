package dev.vitrail.screen;

import java.nio.ByteBuffer;
import java.nio.file.Path;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import net.minecraft.client.Minecraft;
import org.lwjgl.sdl.SDLDialog;
import org.lwjgl.sdl.SDLError;
import org.lwjgl.sdl.SDLProperties;
import org.lwjgl.sdl.SDL_DialogFileCallback;
import org.lwjgl.sdl.SDL_DialogFileFilter;
import org.lwjgl.system.MemoryUtil;

/** Asynchronous native SDL dialogs, with callback and filter storage held until completion. */
public final class FileDialog {
    private static final Set<Request> ACTIVE = ConcurrentHashMap.newKeySet();

    public enum Kind { OPEN, SAVE }

    private FileDialog() {
    }

    public static CompletableFuture<Optional<Path>> choose(Kind kind, String title, Path origin) {
        CompletableFuture<Optional<Path>> answer = new CompletableFuture<>();
        Minecraft.getInstance().schedule(() -> {
            Request request = new Request(answer);
            ACTIVE.add(request);
            try {
                request.show(kind, title, origin);
            } catch (RuntimeException | LinkageError e) {
                request.close();
                answer.completeExceptionally(e);
            }
        });
        return answer;
    }

    private static final class Request {
        private final CompletableFuture<Optional<Path>> answer;
        private final ByteBuffer label = MemoryUtil.memUTF8("Shader Pack Settings (.txt)");
        private final ByteBuffer pattern = MemoryUtil.memUTF8("txt");
        private final SDL_DialogFileFilter.Buffer filters = SDL_DialogFileFilter.calloc(1);
        private final SDL_DialogFileCallback callback;
        private int properties;

        private Request(CompletableFuture<Optional<Path>> answer) {
            this.answer = answer;
            this.filters.get(0).name(this.label).pattern(this.pattern);
            this.callback = SDL_DialogFileCallback.create((userdata, files, filter) -> {
                try {
                    if (files == 0L) {
                        this.answer.completeExceptionally(new IllegalStateException(SDLError.SDL_GetError()));
                    } else {
                        long first = MemoryUtil.memGetAddress(files);
                        this.answer.complete(first == 0L ? Optional.empty()
                                : Optional.of(Path.of(MemoryUtil.memUTF8(first))));
                    }
                } catch (RuntimeException e) {
                    this.answer.completeExceptionally(e);
                } finally {
                    // Queue disposal after this native callback returns, even on the main thread.
                    Minecraft.getInstance().schedule(this::close);
                }
            });
        }

        private void show(Kind kind, String title, Path origin) {
            this.properties = SDLProperties.SDL_CreateProperties();
            if (this.properties == 0) {
                throw new IllegalStateException(SDLError.SDL_GetError());
            }
            SDLProperties.SDL_SetStringProperty(this.properties, SDLDialog.SDL_PROP_FILE_DIALOG_TITLE_STRING, title);
            SDLProperties.SDL_SetStringProperty(this.properties, SDLDialog.SDL_PROP_FILE_DIALOG_LOCATION_STRING, origin.toAbsolutePath().toString());
            SDLProperties.SDL_SetPointerProperty(this.properties, SDLDialog.SDL_PROP_FILE_DIALOG_FILTERS_POINTER, this.filters.address());
            SDLProperties.SDL_SetNumberProperty(this.properties, SDLDialog.SDL_PROP_FILE_DIALOG_NFILTERS_NUMBER, 1L);
            SDLProperties.SDL_SetPointerProperty(this.properties, SDLDialog.SDL_PROP_FILE_DIALOG_WINDOW_POINTER,
                    Minecraft.getInstance().getWindow().handle());
            SDLDialog.SDL_ShowFileDialogWithProperties(kind == Kind.SAVE
                    ? SDLDialog.SDL_FILEDIALOG_SAVEFILE : SDLDialog.SDL_FILEDIALOG_OPENFILE,
                    this.callback, 0L, this.properties);
        }

        private void close() {
            if (ACTIVE.remove(this)) {
                if (this.properties != 0) {
                    SDLProperties.SDL_DestroyProperties(this.properties);
                }
                this.callback.free();
                this.filters.free();
                MemoryUtil.memFree(this.label);
                MemoryUtil.memFree(this.pattern);
            }
        }
    }
}

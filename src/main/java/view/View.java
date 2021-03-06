package view;

import com.bulletphysics.linearmath.Transform;
import model.Body;
import model.RunState;
import org.lwjgl.BufferUtils;
import org.lwjgl.LWJGLException;
import org.lwjgl.input.Keyboard;
import org.lwjgl.input.Mouse;
import org.lwjgl.opengl.Display;
import org.lwjgl.opengl.DisplayMode;
import util.Program;
import view.event.CloseRequestedViewEvent;
import view.event.PropertyChangeRequestViewEvent;
import view.event.StateChangeRequestedViewEvent;
import view.event.ViewEvent;

import javax.vecmath.Vector3f;
import java.nio.FloatBuffer;
import java.util.ArrayList;
import java.util.List;
import java.util.Observable;

import static org.lwjgl.opengl.GL11.*;
import static org.lwjgl.opengl.GL20.glGetUniformLocation;
import static org.lwjgl.opengl.GL20.glUniformMatrix4;

/**
 * Класс представления. Представление умеет визуализировать симуляцию.
 *
 * @author Mike Sorokin
 */
public class View extends Observable {
    private Camera camera;
    private Program program = null;
    private int width, height;
    private boolean drawAxes, confEnabled, mouseGrabbed;

    public View(DisplayMode displayMode,
                boolean fullscreen,
                boolean vSync,
                boolean resizable,
                boolean mouseGrabbed,
                String title,
                Camera camera,
                String vertexShaderName,
                String fragmentShaderName,
                boolean drawAxes,
                boolean confEnabled) {
        try {
            if (!fullscreen) {
                Display.setDisplayMode(displayMode);
            } else {
                Display.setFullscreen(true);
            }
            Display.setVSyncEnabled(vSync);
            Display.setResizable(resizable);
            Mouse.setGrabbed(this.mouseGrabbed = mouseGrabbed);
            Display.setTitle(title);
            Display.create();
        } catch (LWJGLException e) {
            e.printStackTrace();
        }
        this.camera = camera;
        this.width = Display.getWidth();
        this.height = Display.getHeight();
        glEnable(GL_DEPTH_TEST);
        glLight(GL_LIGHT0, GL_AMBIENT, asFloatBuffer(.5f, .5f, .5f, 1));
        glLight(GL_LIGHT0, GL_DIFFUSE, asFloatBuffer(1, 1, 1, 1));
        glLight(GL_LIGHT0, GL_SPECULAR, asFloatBuffer(10, 10, 10, 1));
        glLight(GL_LIGHT0, GL_POSITION, asFloatBuffer(0, 0, -60, 1));
        glDepthFunc(GL_LEQUAL);
        glShadeModel(GL_SMOOTH);
        if (vertexShaderName == null | fragmentShaderName == null) {
            this.program = Program.DUMMY;
        } else {
            this.program = new Program(vertexShaderName, fragmentShaderName);
        }
        this.drawAxes = drawAxes;
        this.confEnabled = confEnabled;
    }

    private FloatBuffer asFloatBuffer(float... floats) {
        FloatBuffer buffer = BufferUtils.createFloatBuffer(floats.length);
        buffer.put(floats);
        buffer.flip();
        return buffer;
    }

    /**
     * Удаляет объекты, используемые представлением, причем после этого оно будет непригодно к использованию.
     * Обязательно вызывать перед завершением работы с этим представлением.
     */
    public void delete() {
        if (program != null)
            program.delete();
        Display.destroy();
    }

    /**
     * Визуализирует симуляцию.
     *
     * @param bodies тела в симуляции (возвращаются методом {@link model.Model#getBodies()})
     */
    public void show(List<Body> bodies) {
        glMatrixMode(GL_PROJECTION);
        glLoadIdentity();
        camera.applyProjectionMatrix();
        glMatrixMode(GL_MODELVIEW);
        glClear(GL_COLOR_BUFFER_BIT | GL_DEPTH_BUFFER_BIT);
        glLoadIdentity();
        camera.applyViewMatrix();
        program.use();
        FloatBuffer viewMatrixReadBuffer = BufferUtils.createFloatBuffer(16);
        glGetFloat(GL_MODELVIEW_MATRIX, viewMatrixReadBuffer);
        float[] viewMatrix = new float[16];
        viewMatrixReadBuffer.get(viewMatrix);
        FloatBuffer viewMatrixWriteBuffer = BufferUtils.createFloatBuffer(16);
        viewMatrixWriteBuffer.put(viewMatrix);
        viewMatrixWriteBuffer.flip();
        glUniformMatrix4(glGetUniformLocation(program.program, "viewMatrix"), false, viewMatrixWriteBuffer);
        bodies.stream().forEach(body -> {
            glPushMatrix();
            float[] mat = new float[16];
            body.getRigidBody().getMotionState().getWorldTransform(new Transform()).getOpenGLMatrix(mat);
            FloatBuffer buffer = BufferUtils.createFloatBuffer(16);
            buffer.put(mat);
            buffer.flip();
            glMultMatrix(buffer);
            body.getList().call();
            glPopMatrix();
        });
        Program.useDummy();
        if (drawAxes) {
            glClear(GL_DEPTH_BUFFER_BIT);
            glBegin(GL_LINES);
            glColor3f(1, 0, 0);
            glVertex3f(-1000, 0, 0);
            glVertex3f(+1000, 0, 0);
            glColor3f(0, 1, 0);
            glVertex3f(0, -1000, 0);
            glVertex3f(0, +1000, 0);
            glColor3f(0, 0, 1);
            glVertex3f(0, 0, -1000);
            glVertex3f(0, 0, +1000);
            glEnd();
            bodies.stream().forEach(body -> {
                Vector3f c = body.getRigidBody().getWorldTransform(new Transform()).origin;
                Vector3f v = body.getRigidBody().getLinearVelocity(new Vector3f());
                glBegin(GL_LINES);
                glColor3f(1, 0, 0);
                glVertex3f(c.x, c.y, c.z);
                glVertex3f(c.x, 0, 0);
                glColor3f(0, 1, 0);
                glVertex3f(c.x, c.y, c.z);
                glVertex3f(0, c.y, 0);
                glColor3f(0, 0, 1);
                glVertex3f(c.x, c.y, c.z);
                glVertex3f(0, 0, c.z);
                glColor3f(1, 1, 0);
                glVertex3f(c.x, c.y, c.z);
                glVertex3f(c.x + v.x, c.y + v.y, c.z + v.z);
                glEnd();
            });
        }
        glMatrixMode(GL_PROJECTION);
        glLoadIdentity();
        glOrtho(0, Display.getWidth(), Display.getHeight(), 0, 1, -1);
        glMatrixMode(GL_MODELVIEW);
        glClear(GL_DEPTH_BUFFER_BIT);
        glLoadIdentity();
        glColor3f(1, 1, 1);
        glBegin(GL_QUADS);

        glVertex2f(width / 2 - 2, height / 2 - 10);
        glVertex2f(width / 2 - 2, height / 2 + 10);
        glVertex2f(width / 2 + 2, height / 2 + 10);
        glVertex2f(width / 2 + 2, height / 2 - 10);

        glVertex2f(width / 2 - 10, height / 2 - 2);
        glVertex2f(width / 2 - 10, height / 2 + 2);
        glVertex2f(width / 2 + 10, height / 2 + 2);
        glVertex2f(width / 2 + 10, height / 2 - 2);

        glEnd();

        Display.update();
        List<ViewEvent> events = new ArrayList<>();
        while (Keyboard.next()) {
            if (Keyboard.getEventKeyState()) {
                switch (Keyboard.getEventKey()) {
                    case Keyboard.KEY_ESCAPE:
                        events.add(new CloseRequestedViewEvent(this));
                        break;
                    case Keyboard.KEY_O:
                        this.drawAxes = this.drawAxes ^ Keyboard.getEventKeyState();
                        break;
                    case Keyboard.KEY_F1:
                        events.add(new StateChangeRequestedViewEvent(this, RunState.TEST));
                        break;
                    case Keyboard.KEY_F2:
                        events.add(new StateChangeRequestedViewEvent(this, RunState.CONF));
                        break;
                }
            }
        }
        while (Mouse.next()) {
            if (confEnabled && Mouse.getEventButton() == 0 && Mouse.getEventButtonState()) {
                events.add(new PropertyChangeRequestViewEvent(this));
            }
        }
        setChanged();
        notifyObservers(events);
    }

    public void setConfEnabled(boolean confEnabled) {
        this.confEnabled = confEnabled;
    }

    public Camera getCamera() {
        return camera;
    }

    public boolean getMouseGrabbed() {
        return mouseGrabbed;
    }
}

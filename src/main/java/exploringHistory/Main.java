package exploringHistory;

import java.awt.AWTException;
import java.awt.Desktop;
import java.awt.Image;
import java.awt.MenuItem;
import java.awt.PopupMenu;
import java.awt.SystemTray;
import java.awt.Toolkit;
import java.awt.TrayIcon;
import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.lang.invoke.MethodHandles;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.text.DecimalFormat;
import java.text.SimpleDateFormat;
import java.time.LocalDate;
import java.util.Arrays;
import java.util.Date;
import java.util.LinkedList;
import java.util.List;
import java.util.Timer;
import java.util.TimerTask;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicLong;
import java.util.concurrent.locks.ReentrantLock;
import java.util.function.ObjIntConsumer;
import java.util.regex.Pattern;

import mmarquee.automation.AutomationException;
import mmarquee.automation.ElementNotFoundException;
import mmarquee.automation.UIAutomation;
import mmarquee.automation.controls.ToolBar;
import mmarquee.automation.controls.Window;

public final class Main {
    private static final String[] TrayMenu = { "Show Log Folder", "Exit" };
    private static final String TrayIconRes = "search.png"; // https://www.flaticon.com/premium-icon/search_954591
    private static final Long INTERVAL = 60L;
    private static final String ExplorerClass = "CabinetWClass";
    private static final String AddrId = "1001";;
    private static final String NoExplorerFound = "(No Explorer Found)";
    private static final String EMPTY = "";;
    private static final Pattern AddrNamePrefix = Pattern.compile(".+\\:\\ ");
    private static final char Comma = ',';
    private static final char LF = '\n';
    private static final StringBuilder CsvLn = new StringBuilder(4096);
    private static final DecimalFormat SEQFT = new DecimalFormat("0000");
    private static final List<String> CONTENT = new LinkedList<>();
    private static final DecimalFormat DF = new DecimalFormat("00.0");
    private static final SimpleDateFormat SDF = new SimpleDateFormat("yyyy-MM-dd HH:mm:ss");
    private static final AtomicLong CNT = new AtomicLong();
    private static final ReentrantLock Mutex = new ReentrantLock();
    private static final String OutFile = LocalDate.now().toString().concat(".csv");
    private static final byte[] BomUtf8 = { (byte) 0xEF, (byte) 0xBB, (byte) 0xBF };
    private static File REC;
    private static FileOutputStream FOS;

    private static final void findWindow() {
        CsvLn.setLength(0);
        CONTENT.clear();

        final String no = SEQFT.format(CNT.incrementAndGet());
        final String ts = SDF.format(new Date());
        final ObjIntConsumer<String> makeCsvLn = (content, parallel) -> CsvLn.append(no).append(Comma).append(ts)
                .append(Comma).append(DF.format(INTERVAL.doubleValue() / parallel)).append(Comma).append(content)
                .append(LF);
        try {
            for (Window win : UIAutomation.getInstance().getDesktopWindows()) {
                if (ExplorerClass.equals(win.getClassName())) {
                    String addr = EMPTY;
                    try {
                        final ToolBar tb = win.getToolBarByAutomationId(AddrId);
                        addr = AddrNamePrefix.matcher(tb.getName()).replaceFirst(EMPTY);
                    } catch (ElementNotFoundException e) {
                        addr = win.getName();
                    } finally {
                        CONTENT.add(addr);
                    }
                }
            }
        } catch (AutomationException e) {
            CONTENT.clear();
            CONTENT.add(e.getMessage());
        }
        if (CONTENT.isEmpty()) {
            CONTENT.add(NoExplorerFound);
        }
        CONTENT.forEach(e -> makeCsvLn.accept(e, CONTENT.size()));

        Mutex.lock();
        try {
            FOS.write(CsvLn.toString().getBytes(StandardCharsets.UTF_8));
            FOS.flush();
        } catch (Exception e) {
        }
        Mutex.unlock();
    }

    private static final void setTray() {
        final SystemTray st = SystemTray.getSystemTray();
        final Image image = Toolkit.getDefaultToolkit().createImage(ClassLoader.getSystemResource(TrayIconRes));
        final PopupMenu popup = new PopupMenu();
        final TrayIcon icon = new TrayIcon(image, MethodHandles.lookup().lookupClass().getPackage().getName(), popup);
        icon.setImageAutoSize(true);
        Arrays.asList(TrayMenu).stream().map(MenuItem::new).forEachOrdered(popup::add);
        popup.getItem(0).addActionListener(e -> {
            try {
                Desktop.getDesktop().open(REC.getParentFile());
            } catch (IOException e1) {
            }
        });
        popup.getItem(1).addActionListener(e -> {
            Mutex.lock();
            st.remove(icon);
            System.exit(0);
        });
        try {
            st.add(icon);
        } catch (AWTException e) {
        }
    }

    public static final void main(String[] args) throws IOException {
        final Path rec = Paths.get(0 == args.length ? EMPTY : args[0], OutFile);
        REC = rec.toFile().getCanonicalFile();
        FOS = new FileOutputStream(REC, true);
        if (0 == Files.size(rec)) {
            FOS.write(BomUtf8);
        }

        setTray();

        final Timer timer = new Timer(true);
        final TimerTask task = new TimerTask() {
            @Override
            public void run() {
                findWindow();
            }
        };
        timer.schedule(task, 0, TimeUnit.SECONDS.toMillis(INTERVAL.longValue()));
    }
}

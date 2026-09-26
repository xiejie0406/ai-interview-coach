package com.ruoyi;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.jdbc.autoconfigure.DataSourceAutoConfiguration;

/**
 * 启动程序
 * 
 * @author ruoyi
 */
@SpringBootApplication(exclude = { DataSourceAutoConfiguration.class })
public class RuoYiApplication
{
    public static void main(String[] args)
    {
        if (args.length == 1 && "--init-managed-jwt".equals(args[0]))
        {
            ManagedSecretBootstrapCli.initialize("platform.ruoyi.jwt");
            return;
        }
        if (args.length == 1 && "--init-managed-aden-pepper".equals(args[0]))
        {
            ManagedSecretBootstrapCli.initialize("platform.aden.runner-pepper");
            return;
        }
        if (args.length == 1 && args[0].startsWith("--init-managed-platform="))
        {
            ManagedSecretBootstrapCli.initialize(args[0].substring("--init-managed-platform=".length()));
            return;
        }
        if (args.length >= 2 && "--run-fashion-ai-runtime".equals(args[0]))
        {
            int status = ManagedFashionRuntimeLauncher.launch(
                    java.util.Arrays.copyOfRange(args, 1, args.length));
            if (status != 0) throw new IllegalStateException("Fashion Runtime 退出码：" + status);
            return;
        }
        // System.setProperty("spring.devtools.restart.enabled", "false");
        SpringApplication application = new SpringApplication(RuoYiApplication.class);
        application.addInitializers(context ->
                ManagedSecretBootstrapProperties.install(context.getEnvironment()));
        application.run(args);
        System.out.println("(♥◠‿◠)ﾉﾞ  若依启动成功   ლ(´ڡ`ლ)ﾞ  \n" +
                " .-------.       ____     __        \n" +
                " |  _ _   \\      \\   \\   /  /    \n" +
                " | ( ' )  |       \\  _. /  '       \n" +
                " |(_ o _) /        _( )_ .'         \n" +
                " | (_,_).' __  ___(_ o _)'          \n" +
                " |  |\\ \\  |  ||   |(_,_)'         \n" +
                " |  | \\ `'   /|   `-'  /           \n" +
                " |  |  \\    /  \\      /           \n" +
                " ''-'   `'-'    `-..-'              ");
    }
}

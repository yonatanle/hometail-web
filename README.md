# HomeTail Web Application

HomeTail is a web application for managing animal adoptions, built with JavaServer Faces (JSF) and PrimeFaces.

## Prerequisites

- Java 21 or higher
- Apache Maven 3.6.0 or higher
- A Java EE 9+ compatible application server (e.g., Payara, WildFly, or TomEE)
- Node.js and npm (for frontend development, if applicable)

## Getting Started

### 1. Clone the Repository

```bash
git clone https://github.com/yonatanle/hometail-web.git
cd homeTail-web
```

### 2. Build the Project

```bash
mvn clean package
```

This will compile the project and create a WAR file in the `target` directory.

### 3. Deploy the Application

#### For Payara Server:

1. Download and install [Payara Server](https://www.payara.fish/software/payara-platform-community-edition/)
2. Deploy the WAR file:
   ```bash
   $HOME/payara6/glassfish/bin/asadmin deploy target/homeTail-web.war
   ```
3. The application will be available at: `http://localhost:8080/homeTail-web/welcome.xhtml`

## Development Setup

### Required Tools

- IntelliJ IDEA (recommended) or Eclipse with Java EE support
- Lombok plugin for your IDE
- Java EE 9+ support in your IDE

### Running in Development Mode

1. Set up your application server in your IDE
2. Configure the server to deploy the exploded WAR
3. Start the server in debug mode
4. The application will automatically reload when you make changes to the code

## Project Structure

```
src/
├── main/
   ├── java/              # Java source files
   │   └── com/hometail/  # Main package
   └── webapp/            # Web application files
       ├── resources/     # Static resources (CSS, JS, images)
       └── WEB-INF/       # Configuration files
           ├── faces-config.xml
           └── web.xml

```

## Dependencies

- **JSF 3.0** - JavaServer Faces implementation
- **PrimeFaces 14.0.2** - UI component library
- **Jakarta EE 9+** - Enterprise Java APIs
- **Jackson** - JSON processing
- **Lombok** - Reduced boilerplate code

## Configuration

The application requires a backend API server running at `http://localhost:9090/api` by default. Make sure the API server is running before starting the application.

## Building for Production

To create a production-ready WAR file:

```bash
mvn clean package -Pproduction
```

## License

[Specify your license here]

## Support

For support, please contact [support email] or open an issue in the repository.

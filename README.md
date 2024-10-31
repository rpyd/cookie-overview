## Overview

"Cookie Overview" is a Burp Suite extension that provides testers with an overview of all cookies used by an application. The extension cross-references these cookies with known analytics and marketing cookies, enabling testers to quickly identify important ones.

Select requests from the **proxy history** or **site map** and use the context menu to “Identify unique cookies”:

![screenshot](/demo-screenshots/1-overview.png)

Selecting "Identify unique cookies" lists all cookies used by the selected requests, categorizing them as "Known" (present in the Open-Cookie-Database) or "Unknown." This idea is inspired by the [Cookie Monster extension](https://github.com/baegmon/CookieMonster).

The results are presented in the Extensions tab:

![screenshot](/demo-screenshots/2-output.png)

The extension is bundled with the Open-Cookie-Database CSV file, but testers can use an updated CSV file by selecting "Select OpenCookieDatabase CSV file and identify unique cookies" in the context menu.

![screenshot](/demo-screenshots/3-database.png)

The screenshots above use Portswigger’s Gin and Juice Shop application as an example. We see a number of AWS load balancer cookies. For a production application, it is likely there will be more cookies for analytics, marketing, etc. Hence, this extension can help testers quickly get an overview of all cookies used by an app and identify important ones.

---

## Alternative - Using Bambdas and writing to a file

Before creating the extension, I used the following bambda to identify all unique cookies and write them to a file.

```java
String dataFilePath = "cookies.txt";


Set<String> uniqueCookieNames = new HashSet<>();

if (!requestResponse.request().isInScope()) {
    return false;
}

List<ParsedHttpParameter> myCookieList = new ArrayList<>();

myCookieList = requestResponse.request().parameters(HttpParameterType.COOKIE);

// idk man
try (BufferedReader reader = new BufferedReader(new FileReader(dataFilePath))) {
    String line;
    while ((line = reader.readLine()) != null) {
        uniqueCookieNames.add(line.trim());
    }
} catch (IOException e) {
    e.printStackTrace();
}
try (BufferedWriter writer = new BufferedWriter(new FileWriter(dataFilePath, true))) {
    for (ParsedHttpParameter cookie : myCookieList) {
        if (uniqueCookieNames.add(cookie.name())) {
            writer.write(cookie.name() + "\n");
        }
    }
} catch (IOException e) {
    e.printStackTrace();
}


return false;
```

The bambda generates “cookies.txt”:
```
AWSALB
AWSALBCORS
session
```

I then used a bash script to compare them with the CSV file from the Open-Cookie-Database: 

```bash
#!/bin/bash

# Define file names
csv_file="open-cookie-database.csv"
text_file="cookies.txt"

# Create temporary files
matched_cookies=$(mktemp)
unmatched_cookies=$(mktemp)

# Process each cookie in the text file
while IFS= read -r cookie_name; do
  # Trim any leading/trailing whitespace from the cookie_name
  cookie_name=$(echo "$cookie_name" | xargs)
  
  # Skip empty lines
  if [ -z "$cookie_name" ]; then
    continue
  fi
  
  # Check if the cookie is present in the specific column of the CSV file
  if awk -F, -v cookie="$cookie_name" '$4 == cookie {found=1} END {exit !found}' "$csv_file"; then
    # If found, extract the description and store in matched_cookies
    awk -F, -v cookie="$cookie_name" '
    $4 == cookie {
      print "------------------------"
      print "Cookie: " $4
      print "Description: \"" $6 "\""
      print "------------------------"
    }' "$csv_file" >> "$matched_cookies"
  else
    # If not found, store the cookie name in unmatched_cookies
    echo "$cookie_name" >> "$unmatched_cookies"
  fi
done < "$text_file"

# Display matched cookies
cat "$matched_cookies"

# Display unmatched cookies
if [ -s "$unmatched_cookies" ]; then
  echo "===== UNMATCHED COOKIES ====="
  cat "$unmatched_cookies"
fi

# Clean up temporary files
rm "$matched_cookies" "$unmatched_cookies"

```
Running the bash script generates the following output:
```
------------------------
Cookie: AWSALB
Description: ""These cookies enable us to allocate server traffic to make the user experience as smooth as possible. A so-called load balancer is used to determine which server currently has the best availability. The information generated cannot identify you as an individual.""
------------------------
------------------------
Cookie: AWSALBCORS
Description: ""For continued stickiness support with CORS use cases after the Chromium update"
------------------------
===== UNMATCHED COOKIES =====
session
```
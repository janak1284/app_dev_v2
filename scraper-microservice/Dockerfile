# Use the official Playwright image which includes all browser dependencies
FROM mcr.microsoft.com/playwright:v1.40.0-jammy

# Hugging Face runs containers as user '1000'
# We must set up a working directory with the correct permissions
WORKDIR /app
RUN chown -R 1000:1000 /app
USER 1000

# Copy package files and install dependencies
COPY --chown=1000:1000 package*.json ./
RUN npm install

# Copy the rest of the application files (scraper, mapper, geojson, server)
COPY --chown=1000:1000 . .

# Expose the port Hugging Face looks for
EXPOSE 7860

# Start the server
CMD ["node", "server.js"]
